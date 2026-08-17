import SwiftUI

struct ContentView: View {
    @Environment(AppModel.self) private var model
    @State private var showAddSite = false

    var body: some View {
        @Bindable var model = model
        NavigationStack(path: $model.path) {
            Group {
                if model.activeAccount == nil {
                    AccountLandingView(onAddSite: { showAddSite = true })
                } else {
                    PortalShellView(onAddSite: { showAddSite = true })
                }
            }
            .navigationDestination(for: AppRoute.self) { route in destination(route) }
        }
        .sheet(isPresented: $showAddSite, onDismiss: { model.pendingConfig = nil }) {
            NavigationStack { AddSiteView().environment(model) }
        }
        .task { await model.start() }
        .onOpenURL { url in if url.scheme == SSOProtocol.callbackScheme { Task { await model.handleSSOCallback(url) } } }
        .onAppear { NotificationRouter.shared.route = { accountID, conversationID in model.receiveNotification(accountID: accountID, conversationID: conversationID) } }
    }

    @ViewBuilder private func destination(_ route: AppRoute) -> some View {
        switch route {
        case .course(let id):
            if let course = model.snapshot.courses.first(where: { $0.id == id }) { CourseDetailView(course: course) }
            else { PortalEmptyState(icon: "exclamationmark.triangle", title: "course.missing.title", message: "course.missing.body").padding() }
        case .module(_, let module): ModuleContentView(module: module)
        case .assignment(let assignment): AssignmentView(assignment: assignment)
        case .conversation(let id): ConversationView(conversationID: id)
        case .newMessage: NewMessageView()
        }
    }
}
