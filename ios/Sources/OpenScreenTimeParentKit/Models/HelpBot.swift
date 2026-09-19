import Foundation

/// A cited public source behind a piece of screen-time guidance.
struct HelpSource: Codable, Equatable {
    let title: String
    let url: String
}

/// One question the help bot can answer. Mirrors `shared/model/HelpBot.kt` and reads the very same
/// knowledge base (`Resources/knowledge.json`, a byte-identical copy of
/// `shared/src/main/resources/helpbot/knowledge.json` - a test enforces that).
struct HelpEntry: Codable, Equatable {
    let id: String
    let audience: String
    let topic: String
    let question: String
    let keywords: [String]
    let answer: String
    let sources: [HelpSource]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        audience = try c.decodeIfPresent(String.self, forKey: .audience) ?? "both"
        topic = try c.decode(String.self, forKey: .topic)
        question = try c.decode(String.self, forKey: .question)
        keywords = try c.decode([String].self, forKey: .keywords)
        answer = try c.decode(String.self, forKey: .answer)
        sources = try c.decodeIfPresent([HelpSource].self, forKey: .sources) ?? []
    }

    private enum CodingKeys: String, CodingKey {
        case id, audience, topic, question, keywords, answer, sources
    }
}

struct HelpKnowledge: Codable, Equatable {
    let version: Int
    let entries: [HelpEntry]
    let suggestions: [String: [String]]
}

enum HelpAudience: String {
    case parent
    case kid
}

/// What the bot says back: `text`, any `sources`, and other questions it can answer nearby.
struct HelpReply: Equatable {
    var text: String
    var sources: [HelpSource] = []
    var relatedQuestions: [String] = []
    var matchedEntryId: String?
}

/// See #36 - the in-app help bot. Deliberately not a language model: a small, offline, private,
/// auditable retriever over a curated, cited knowledge base, so a child's question never leaves the
/// phone and a health answer only ever says what a public source says. A line-for-line port of the
/// Android `HelpBot`, so both apps answer identically.
final class HelpBot {
    private let knowledge: HelpKnowledge

    init(knowledge: HelpKnowledge) {
        self.knowledge = knowledge
    }

    /// The one bot the app uses, loaded from the packaged knowledge base.
    static let shared = HelpBot(knowledge: loadKnowledge())

    func suggestions(for audience: HelpAudience) -> [String] {
        knowledge.suggestions[audience.rawValue] ?? []
    }

    func reply(to query: String, audience: HelpAudience) -> HelpReply {
        let visible = knowledge.entries.filter { $0.audience == "both" || $0.audience == audience.rawValue }
        let ordered = Self.tokenizeOrdered(query)
        let tokens = Set(ordered)
        if tokens.isEmpty { return fallback(audience) }

        let age = Self.extractAgeYears(query)
        let aboutLimits = tokens.contains { Self.limitWords.contains($0) }
        var ageEntryId: String?
        if let age, aboutLimits { ageEntryId = Self.guidanceEntryId(forAge: age) }
        let normalizedQuery = " " + ordered.joined(separator: " ") + " "

        var scored: [(entry: HelpEntry, score: Int)] = []
        for entry in visible {
            let total = score(entry, tokens, normalizedQuery) + (entry.id == ageEntryId ? Self.ageBoost : 0)
            if total >= Self.minScore { scored.append((entry, total)) }
        }
        scored.sort { $0.score > $1.score }

        guard let best = scored.first else { return fallback(audience) }
        return HelpReply(
            text: best.entry.answer,
            sources: best.entry.sources,
            relatedQuestions: scored.dropFirst().prefix(Self.maxRelated).map { $0.entry.question },
            matchedEntryId: best.entry.id
        )
    }

    private func fallback(_ audience: HelpAudience) -> HelpReply {
        HelpReply(
            text: "I don't have an answer for that yet. I can help with how to use OpenScreenTime, " +
                "why it's designed the way it is, and what published guidance says about reasonable " +
                "screen time limits. Try one of these:",
            relatedQuestions: suggestions(for: audience)
        )
    }

    private func score(_ entry: HelpEntry, _ queryTokens: Set<String>, _ normalizedQuery: String) -> Int {
        var total = 0
        let keywordTokens = Set(entry.keywords.flatMap { Self.tokenizeOrdered($0) })
        let questionTokens = Set(Self.tokenizeOrdered(entry.question))
        let answerTokens = Set(Self.tokenizeOrdered(entry.answer))
        for t in queryTokens {
            if keywordTokens.contains(t) { total += Self.keywordWeight }
            if questionTokens.contains(t) { total += Self.questionWeight }
            if answerTokens.contains(t) { total += Self.answerWeight }
        }
        for phrase in entry.keywords {
            let stemmed = Self.tokenizeOrdered(phrase)
            if stemmed.count >= 2, normalizedQuery.contains(" " + stemmed.joined(separator: " ") + " ") {
                total += Self.phraseWeight
            }
        }
        return total
    }

    // MARK: - Tuning (must stay equal to the Kotlin constants)

    private static let minScore = 5
    private static let maxRelated = 2
    private static let keywordWeight = 4
    private static let questionWeight = 2
    private static let answerWeight = 1
    private static let phraseWeight = 6
    private static let ageBoost = 100

    private static let limitWords: Set<String> = [
        "limit", "much", "hour", "minute", "allow", "reasonable", "recommend", "should",
        "set", "healthy", "normal", "enough", "long", "time", "screen"
    ]

    private static let stopwords: Set<String> = [
        "a", "an", "the", "is", "are", "was", "were", "do", "does", "did", "how", "what", "i", "my",
        "me", "to", "of", "for", "can", "we", "it", "in", "on", "and", "or", "when", "why", "with",
        "this", "that", "be", "you", "your", "there", "if", "at", "about", "so", "as", "am", "have",
        "has", "any", "some", "get", "them", "they", "their", "would", "could", "please", "tell"
    ]

    // MARK: - Text handling

    /// Lowercased, punctuation-stripped, stopword-free, lightly stemmed tokens - de-duplicated, in order.
    static func tokenizeOrdered(_ text: String) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        let words = text.lowercased().split { !($0.isASCII && ($0.isLetter || $0.isNumber)) }
        for word in words.map(String.init) where !stopwords.contains(word) {
            let stemmed = stem(word)
            if seen.insert(stemmed).inserted { out.append(stemmed) }
        }
        return out
    }

    static func tokenize(_ text: String) -> Set<String> { Set(tokenizeOrdered(text)) }

    private static func stem(_ word: String) -> String {
        if word.count > 5 && word.hasSuffix("ing") { return String(word.dropLast(3)) }
        if word.count > 4 && word.hasSuffix("ies") { return String(word.dropLast(3)) + "y" }
        if word.count > 4 && word.hasSuffix("es") { return String(word.dropLast(2)) }
        if word.count > 3 && word.hasSuffix("s") && !word.hasSuffix("ss") { return String(word.dropLast(1)) }
        return word
    }

    private static let agePatterns: [NSRegularExpression] = [
        #"(\d{1,2})\s*-?\s*(?:year|yr)s?\s*-?\s*old"#,
        #"(?:age|aged)\s*(\d{1,2})"#,
        #"(\d{1,2})\s*(?:yo|y/o)\b"#,
        #"(\d{1,2})\s*(?:month)s?\s*-?\s*old"#
    ].map { try! NSRegularExpression(pattern: $0) }

    /// "my 4 year old", "aged 7", "a 9yo" -> the age in years; months give 0 (an infant).
    static func extractAgeYears(_ query: String) -> Int? {
        let q = query.lowercased()
        let range = NSRange(q.startIndex..., in: q)
        if agePatterns[3].firstMatch(in: q, range: range) != nil { return 0 }
        for pattern in agePatterns.prefix(3) {
            if let m = pattern.firstMatch(in: q, range: range),
               let r = Range(m.range(at: 1), in: q),
               let n = Int(q[r]), (0...99).contains(n) {
                return n
            }
        }
        return nil
    }

    /// Which guidance entry answers "how much screen time" for a child this age.
    static func guidanceEntryId(forAge age: Int) -> String {
        if age < 2 { return "guidance-under-2" }
        if age < 5 { return "guidance-2-4" }
        if age < 18 { return "guidance-5-17" }
        return "guidance-adults"
    }

    // MARK: - Loading

    static func parseKnowledge(_ data: Data) throws -> HelpKnowledge {
        try JSONDecoder().decode(HelpKnowledge.self, from: data)
    }

    /// Where the packaged knowledge base lives (exposed so tests can compare it to the shared copy).
    static var knowledgeResourceURL: URL? {
        Bundle.module.url(forResource: "knowledge", withExtension: "json")
    }

    /// Loads the packaged knowledge base; empty (so the bot only ever falls back) if it can't be read.
    static func loadKnowledge() -> HelpKnowledge {
        guard let url = knowledgeResourceURL,
              let data = try? Data(contentsOf: url),
              let knowledge = try? parseKnowledge(data) else {
            return HelpKnowledge(version: 0, entries: [], suggestions: [:])
        }
        return knowledge
    }
}
