import SwiftUI

struct MessageListView: View {
    @Environment(AppModel.self) private var model
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 6) { Text("messages.eyebrow").font(.caption.bold()).foregroundStyle(PortalTheme.teal).textCase(.uppercase); Text("messages.title").font(.largeTitle.bold()); Text("messages.subtitle").font(.subheadline).foregroundStyle(.secondary) }
                    Spacer()
                    if model.activeAccount?.capabilities.messages.canStartConversation == true {
                        Button { model.path.append(AppRoute.newMessage) } label: { Image(systemName: "square.and.pencil").font(.title3).frame(width: 48, height: 48).background(PortalTheme.teal, in: Circle()).foregroundStyle(.white) }.accessibilityLabel(Text("messages.new"))
                    }
                }.padding(.bottom, 16)
                if model.activeAccount?.capabilities.messages.canList != true { PortalEmptyState(icon: "bubble.left.and.exclamationmark.bubble.right", title: "messages.unavailable.title", message: "messages.unavailable.body") }
                else if model.snapshot.conversations.isEmpty { PortalEmptyState(icon: "bubble.left.and.bubble.right", title: "messages.empty.title", message: "messages.empty.body") }
                else { ForEach(model.snapshot.conversations) { conversation in
                    Button { model.path.append(AppRoute.conversation(conversation.id)) } label: { ConversationRow(conversation: conversation) }.buttonStyle(.plain).accessibilityIdentifier("conversation.\(conversation.id)")
                    Divider().padding(.leading, 72)
                } }
            }.padding(.horizontal, 16).padding(.bottom, 34)
        }.refreshable { await model.refreshConversations() }
    }
}

private struct ConversationRow: View {
    var conversation: MoodleConversation
    var body: some View {
        HStack(spacing: 13) {
            InitialAvatar(name: conversation.name, size: 48)
            VStack(alignment: .leading, spacing: 5) {
                HStack { Text(conversation.name).font(.headline).foregroundStyle(.primary).lineLimit(1); Spacer(); Text(conversation.latestMessageAt, format: .relative(presentation: .named)).font(.caption2).foregroundStyle(.secondary) }
                HStack { Text(conversation.latestMessagePreview.isEmpty ? String(localized: "messages.no_preview") : conversation.latestMessagePreview).font(.subheadline).foregroundStyle(conversation.unreadCount > 0 ? .primary : .secondary).fontWeight(conversation.unreadCount > 0 ? .semibold : .regular).lineLimit(2); Spacer(); if conversation.unreadCount > 0 { Text(String(min(conversation.unreadCount, 99))).font(.caption2.bold()).foregroundStyle(.white).padding(.horizontal, 7).padding(.vertical, 4).background(PortalTheme.teal, in: Capsule()) } }
            }
        }.padding(.vertical, 14).contentShape(Rectangle()).accessibilityElement(children: .combine)
    }
}

struct ConversationView: View {
    @Environment(AppModel.self) private var model
    var conversationID: Int64
    @State private var text = ""
    @State private var loadedOffset = 0
    @FocusState private var composerFocused: Bool
    private var conversation: MoodleConversation? { model.snapshot.conversations.first { $0.id == conversationID } }
    private var messages: [MoodleMessage] { model.snapshot.messages[conversationID] ?? [] }
    var body: some View {
        PortalBackground {
            VStack(spacing: 0) {
                if !model.isOnline { HStack { OfflinePill(); Spacer() }.padding(.horizontal).padding(.vertical, 6) }
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            if messages.count >= 50 { Button("messages.load_older") { loadedOffset += 50; Task { await model.refreshMessages(conversationID: conversationID, offset: loadedOffset) } }.font(.subheadline.weight(.semibold)).padding() }
                            ForEach(Array(grouped.enumerated()), id: \.offset) { _, group in
                                Text(group.day, format: .dateTime.weekday(.wide).month(.abbreviated).day()).font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.vertical, 8)
                                    .frame(maxWidth: .infinity).accessibilityAddTraits(.isHeader)
                                ForEach(group.messages) { message in MessageBubble(message: message).id(message.id) }
                            }
                        }.padding(.horizontal, 14).padding(.vertical, 10)
                    }.onChange(of: messages.count) { _, _ in if let last = messages.last { withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(last.id, anchor: .bottom) } } }
                }
                Divider()
                composer
            }
        }.navigationTitle(conversation?.name ?? String(localized: "conversation")).navigationBarTitleDisplayMode(.inline)
        .task {
            text = model.draft(key: conversationDraftKey(conversationID))
            await model.refreshMessages(conversationID: conversationID, markRead: true)
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(20))
                if !Task.isCancelled && model.isOnline { await model.refreshMessages(conversationID: conversationID, markRead: true) }
            }
        }.onDisappear { model.saveDraft(key: conversationDraftKey(conversationID), body: text) }
    }

    private var composer: some View {
        VStack(spacing: 6) {
            if case .failed(let message) = model.messageSendState { HStack { Image(systemName: "exclamationmark.circle.fill"); Text(message).lineLimit(2); Spacer(); Button("action.retry") { send() } }.font(.caption).foregroundStyle(.red).padding(.horizontal, 16) }
            HStack(alignment: .bottom, spacing: 10) {
                TextField(model.isOnline ? String(localized: "messages.reply") : String(localized: "messages.offline"), text: $text, axis: .vertical).lineLimit(1...5).focused($composerFocused)
                    .padding(.horizontal, 15).padding(.vertical, 12).background(Color.secondary.opacity(0.09), in: RoundedRectangle(cornerRadius: 21))
                Button(action: send) {
                    Group { if model.messageSendState == .sending { ProgressView().tint(.white) } else { Image(systemName: "arrow.up") } }.font(.headline.bold()).frame(width: 44, height: 44).background(PortalTheme.teal, in: Circle()).foregroundStyle(.white)
                }.disabled(!model.isOnline || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.messageSendState == .sending).accessibilityLabel(Text("messages.send"))
            }.padding(.horizontal, 12).padding(.vertical, 9)
        }.background(.ultraThinMaterial)
    }

    private var grouped: [(day: Date, messages: [MoodleMessage])] {
        Dictionary(grouping: messages) { Calendar.current.startOfDay(for: $0.createdAt) }.keys.sorted().map { ($0, Dictionary(grouping: messages) { Calendar.current.startOfDay(for: $0.createdAt) }[$0] ?? []) }
    }
    private func send() { let body = text; Task { if await model.sendMessage(conversationID: conversationID, text: body) { text = ""; composerFocused = true } } }
}

private struct MessageBubble: View {
    var message: MoodleMessage
    var body: some View {
        HStack(alignment: .bottom) {
            if message.isMine { Spacer(minLength: 54) }
            VStack(alignment: message.isMine ? .trailing : .leading, spacing: 4) {
                if !message.isMine { Text(message.senderName).font(.caption.weight(.semibold)).foregroundStyle(.secondary) }
                HTMLText(html: message.bodyHTML.isEmpty ? message.bodyText : message.bodyHTML)
                    .foregroundStyle(message.isMine ? Color.white : Color.primary)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(message.isMine ? AnyShapeStyle(LinearGradient(colors: [PortalTheme.teal, PortalTheme.tealDark], startPoint: .topLeading, endPoint: .bottomTrailing)) : AnyShapeStyle(Color.secondary.opacity(0.1)), in: RoundedRectangle(cornerRadius: 19, style: .continuous))
                Text(message.createdAt, format: .dateTime.hour().minute()).font(.caption2).foregroundStyle(.tertiary)
            }.frame(maxWidth: 520, alignment: message.isMine ? .trailing : .leading)
            if !message.isMine { Spacer(minLength: 54) }
        }.frame(maxWidth: .infinity)
            .accessibilityElement(children: .combine)
            .accessibilityLabel(message.bodyText)
            .accessibilityIdentifier("message.\(message.id)")
    }
}

struct NewMessageView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var selected: MoodleMessageUser?
    @State private var text = ""
    var body: some View {
        PortalBackground {
            VStack(spacing: 0) {
                HStack { Image(systemName: "magnifyingglass").foregroundStyle(.secondary); TextField("messages.search", text: $query).textInputAutocapitalization(.never).autocorrectionDisabled(); if !query.isEmpty { Button { query = ""; selected = nil } label: { Image(systemName: "xmark.circle.fill") } } }.padding(.horizontal, 15).frame(minHeight: 50).background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 16)).padding()
                if let selected {
                    VStack(spacing: 18) {
                        InitialAvatar(name: selected.fullName, size: 64); Text(selected.fullName).font(.title2.bold())
                        TextEditor(text: $text).frame(minHeight: 170).padding(10).scrollContentBackground(.hidden).background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                        Button { Task { if let id = await model.startConversation(userID: selected.id, text: text) { model.path.removeLast(); model.path.append(AppRoute.conversation(id)) } } } label: { Label("messages.send", systemImage: "paperplane.fill").font(.headline).frame(maxWidth: .infinity).frame(minHeight: 52) }.buttonStyle(.borderedProminent).tint(PortalTheme.teal).disabled(!model.isOnline || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }.padding(20)
                } else if query.count < 2 { PortalEmptyState(icon: "person.crop.circle.badge.magnifyingglass", title: "messages.find.title", message: "messages.find.body").padding() }
                else {
                    List(model.searchedUsers) { user in Button { selected = user; text = model.draft(key: userDraftKey(user.id)) } label: { HStack { InitialAvatar(name: user.fullName, size: 42); Text(user.fullName).foregroundStyle(.primary); Spacer(); Image(systemName: "chevron.right").foregroundStyle(.tertiary) } }.buttonStyle(.plain) }.scrollContentBackground(.hidden)
                }
            }
        }.navigationTitle(Text("messages.new")).navigationBarTitleDisplayMode(.inline)
        .task(id: query) { guard query.count >= 2 else { model.searchedUsers = []; return }; try? await Task.sleep(for: .milliseconds(350)); if !Task.isCancelled { await model.searchUsers(query) } }
        .onDisappear { if let selected { model.saveDraft(key: userDraftKey(selected.id), body: text) } }
    }
}
