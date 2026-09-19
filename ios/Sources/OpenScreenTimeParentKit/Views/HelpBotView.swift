import SwiftUI

/// See #36 - the "?" help screen. A chat-shaped front for `HelpBot`: a small, offline, cited
/// retriever, deliberately not a language model, so nothing typed here leaves the phone and health
/// answers only ever say what a public source says. Mirrors the Android `HelpBotScreen`.
struct HelpBotView: View {
    let audience: HelpAudience
    @Environment(\.dismiss) private var dismiss

    /// The prompt the bot opens with.
    static let prompt = "Ask me anything about how to use this app or what are reasonable limits for screen time."

    private struct Message: Identifiable {
        enum Kind {
            case user(String)
            case bot(HelpReply)
        }
        let id = UUID()
        let kind: Kind
    }

    @State private var messages: [Message] = []
    @State private var draft = ""

    private var bot: HelpBot { HelpBot.shared }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 12) {
                            ForEach(messages) { message in
                                row(for: message).id(message.id)
                            }
                        }
                        .padding(12)
                    }
                    .onChange(of: messages.count) { _ in
                        if let last = messages.last {
                            withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                        }
                    }
                }
                Divider()
                inputBar
            }
            .navigationTitle("Help")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .onAppear {
                if messages.isEmpty {
                    messages = [Message(kind: .bot(HelpReply(
                        text: Self.prompt,
                        relatedQuestions: bot.suggestions(for: audience)
                    )))]
                }
            }
        }
    }

    @ViewBuilder
    private func row(for message: Message) -> some View {
        switch message.kind {
        case .user(let text):
            HStack {
                Spacer(minLength: 40)
                Text(text)
                    .padding(12)
                    .background(Color.accentColor.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        case .bot(let reply):
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(reply.text)
                    if !reply.sources.isEmpty {
                        Text("Sources").font(.caption).bold()
                        ForEach(reply.sources, id: \.url) { source in
                            if let url = URL(string: source.url), url.scheme == "https" {
                                Link(source.title, destination: url).font(.footnote)
                            }
                        }
                    }
                }
                .padding(12)
                .background(Color.secondary.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 12))

                if !reply.relatedQuestions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(reply.relatedQuestions, id: \.self) { question in
                                Button(question) { ask(question) }
                                    .buttonStyle(.bordered)
                                    .font(.footnote)
                            }
                        }
                    }
                }
            }
        }
    }

    private var inputBar: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                TextField("Ask a question", text: $draft)
                    .textFieldStyle(.roundedBorder)
                    .submitLabel(.send)
                    .onSubmit { ask(draft) }
                Button("Send") { ask(draft) }
                    .buttonStyle(.borderedProminent)
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            Text(
                "Answers come from a fixed, cited knowledge base - not AI - and nothing you type " +
                "leaves this phone. General guidance, not medical advice."
            )
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(12)
    }

    private func ask(_ question: String) {
        let trimmed = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        messages.append(Message(kind: .user(trimmed)))
        messages.append(Message(kind: .bot(bot.reply(to: trimmed, audience: audience))))
        draft = ""
    }
}
