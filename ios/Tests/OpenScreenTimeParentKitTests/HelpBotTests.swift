import XCTest
@testable import OpenScreenTimeParentKit

/// The Swift port must answer exactly like the Android `HelpBot` - these mirror `HelpBotTest.kt`.
final class HelpBotTests: XCTestCase {

    private let knowledge = HelpBot.loadKnowledge()
    private var bot: HelpBot { HelpBot(knowledge: knowledge) }

    private func matched(_ query: String, _ audience: HelpAudience = .parent) -> String? {
        bot.reply(to: query, audience: audience).matchedEntryId
    }

    func testIPhoneParentsAreToldTheWeeklyReportIsAndroidOnly() {
        let reply = bot.reply(to: "what is the weekly report", audience: .parent)
        XCTAssertEqual(reply.matchedEntryId, "weekly-report")
        XCTAssertTrue(reply.text.contains("iPhone parent app yet"), "iOS must not describe a screen it does not have")
    }

    // MARK: - The knowledge base itself

    func testPackagedKnowledgeBaseLoads() {
        XCTAssertFalse(knowledge.entries.isEmpty, "knowledge.json should be bundled with the package")
    }

    func testBundledCopyIsByteIdenticalToTheSharedOne() throws {
        // #filePath is .../ios/Tests/OpenScreenTimeParentKitTests/HelpBotTests.swift
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let shared = repoRoot.appendingPathComponent("shared/src/main/resources/helpbot/knowledge.json")
        let bundled = try XCTUnwrap(HelpBot.knowledgeResourceURL)
        XCTAssertEqual(try Data(contentsOf: shared), try Data(contentsOf: bundled))
    }

    func testEntryIdsAreUnique() {
        let ids = knowledge.entries.map(\.id)
        XCTAssertEqual(ids.count, Set(ids).count)
    }

    func testEveryHealthGuidanceEntryCitesAtLeastOneSource() {
        for id in ["guidance-overview", "guidance-under-2", "guidance-2-4", "guidance-5-17",
                   "guidance-aap", "guidance-uk-cmo", "guidance-sleep", "guidance-quality"] {
            let entry = knowledge.entries.first { $0.id == id }
            XCTAssertNotNil(entry, id)
            XCTAssertFalse(entry?.sources.isEmpty ?? true, "\(id) should cite a source")
        }
    }

    func testEverySourceHasAnHttpsUrl() {
        for source in knowledge.entries.flatMap(\.sources) {
            XCTAssertFalse(source.title.isEmpty)
            XCTAssertTrue(source.url.hasPrefix("https://"))
        }
    }

    // MARK: - Reachability

    func testEveryEntryIsReachableThroughItsOwnQuestion() {
        for audience in [HelpAudience.parent, .kid] {
            for entry in knowledge.entries where entry.audience == "both" || entry.audience == audience.rawValue {
                XCTAssertEqual(
                    matched(entry.question, audience), entry.id,
                    "'\(entry.question)' (\(audience.rawValue)) should reach \(entry.id)"
                )
            }
        }
    }

    func testEverySuggestedQuestionGetsAnAnswer() {
        for audience in [HelpAudience.parent, .kid] {
            for question in bot.suggestions(for: audience) {
                XCTAssertNotNil(matched(question, audience), question)
            }
        }
    }

    // MARK: - Age-aware limits

    func testAgesRouteToTheRightGuidance() {
        XCTAssertEqual(matched("How much screen time should my 1 year old have?"), "guidance-under-2")
        XCTAssertEqual(matched("screen time limit for my 10 month old"), "guidance-under-2")
        XCTAssertEqual(matched("How much screen time is ok for my 4 year old?"), "guidance-2-4")
        XCTAssertEqual(matched("What screen time limit should I set for my 7 year old"), "guidance-5-17")
        XCTAssertEqual(matched("how many hours a day for a 14-year-old"), "guidance-5-17")
        XCTAssertEqual(matched("How much screen time should I set for myself?"), "guidance-adults")
    }

    func testForgettingTheAccountPasswordReachesTheResetEntry() {
        XCTAssertEqual(matched("I forgot my password"), "password-reset")
        XCTAssertEqual(matched("how do I reset my password"), "password-reset")
    }

    func testAgeExtractionHandlesTheCommonPhrasings() {
        XCTAssertEqual(HelpBot.extractAgeYears("my 4 year old"), 4)
        XCTAssertEqual(HelpBot.extractAgeYears("a 9-year-old"), 9)
        XCTAssertEqual(HelpBot.extractAgeYears("aged 12"), 12)
        XCTAssertEqual(HelpBot.extractAgeYears("my 7yo"), 7)
        XCTAssertEqual(HelpBot.extractAgeYears("my 8 month old"), 0)
        XCTAssertNil(HelpBot.extractAgeYears("how do I set a bedtime"))
    }

    func testAgeExtractionHandlesCompoundAgesAndIgnoresLookAlikes() {
        XCTAssertEqual(HelpBot.extractAgeYears("my 1 year 6 months old"), 1)
        XCTAssertEqual(HelpBot.extractAgeYears("a 2 years and 3 months old"), 2)
        XCTAssertEqual(HelpBot.extractAgeYears("my 24 month old"), 2)
        XCTAssertNil(HelpBot.extractAgeYears("in 2024 year old phones are common"))
        XCTAssertNil(HelpBot.extractAgeYears("usage 10 hours"))
    }

    func testPluralsStemToTheSameTokenAsTheirSingular() {
        XCTAssertEqual(HelpBot.tokenize("time"), HelpBot.tokenize("times"))
        XCTAssertEqual(HelpBot.tokenize("note"), HelpBot.tokenize("notes"))
        XCTAssertEqual(HelpBot.tokenize("watch"), HelpBot.tokenize("watches"))
    }

    // MARK: - How-to, philosophy, audience, fallback

    func testCommonQuestionsReachTheRightEntry() {
        XCTAssertEqual(matched("how do I set a bedtime?"), "bedtime")
        XCTAssertEqual(matched("how do I pair my child's phone"), "pairing")
        XCTAssertEqual(matched("what is the weekly report"), "weekly-report")
        XCTAssertEqual(matched("why can't my child see their exact numbers"), "why-no-numbers-kid")
        XCTAssertEqual(matched("how do I ask for more time", .kid), "request-more-time")
    }

    func testAKidNeverGetsParentOnlySetupAnswers() {
        XCTAssertNotEqual(matched("how do I pair my child's phone", .kid), "pairing")
    }

    func testAnUnrelatedQuestionFallsBackHonestlyWithSuggestions() {
        let reply = bot.reply(to: "what is the airspeed velocity of an unladen swallow", audience: .parent)
        XCTAssertNil(reply.matchedEntryId)
        XCTAssertTrue(reply.text.hasPrefix("I don't have an answer"))
        XCTAssertEqual(reply.relatedQuestions, bot.suggestions(for: .parent))
    }

    func testTokenizingDropsStopwordsAndLightlyStems() {
        XCTAssertEqual(HelpBot.tokenize("What are the limits for apps?"), ["limit", "app"])
    }
}
