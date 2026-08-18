import SwiftUI

struct AccountLandingView: View {
    @Environment(AppModel.self) private var model
    var onAddSite: () -> Void

    var body: some View {
        PortalBackground {
            ScrollView {
                VStack(spacing: 28) {
                    VStack(spacing: 16) {
                        PortalBrandMark(size: 68)
                        Text("app.name").font(.system(.largeTitle, design: .rounded, weight: .bold)).foregroundStyle(PortalTheme.navy)
                        Text("onboarding.tagline").font(.title3).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }.padding(.top, 52)

                    VStack(alignment: .leading, spacing: 18) {
                        Label("onboarding.secure.title", systemImage: "lock.shield.fill").font(.headline).foregroundStyle(PortalTheme.teal)
                        Text("onboarding.secure.body").font(.subheadline).foregroundStyle(.secondary)
                        HStack(spacing: 18) {
                            feature("iphone", "onboarding.native")
                            feature("externaldrive.fill", "onboarding.offline")
                            feature("building.columns.fill", "onboarding.anysite")
                        }
                    }.padding(22).portalCard()

                    if !model.accounts.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            PortalSectionHeader(title: "accounts.title")
                            ForEach(model.accounts) { account in
                                Button { Task { await model.activate(account.id) } } label: {
                                    HStack(spacing: 14) {
                                        InitialAvatar(name: account.fullName ?? account.siteName)
                                        VStack(alignment: .leading, spacing: 3) {
                                            Text(account.fullName ?? account.username ?? String(localized: "account")).font(.headline).foregroundStyle(.primary)
                                            Text(account.siteName).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                                        }
                                        Spacer()
                                        Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                                    }.padding(16).contentShape(Rectangle())
                                }.buttonStyle(.plain).portalCard(radius: 16)
                            }
                        }
                    }

                    Button(action: onAddSite) {
                        Label("onboarding.connect", systemImage: "plus.circle.fill").font(.headline).frame(maxWidth: .infinity).frame(minHeight: 52)
                    }.buttonStyle(.borderedProminent).buttonBorderShape(.roundedRectangle(radius: 16)).tint(PortalTheme.teal).accessibilityIdentifier("add-site.button")
                }.frame(maxWidth: 560).padding(.horizontal, 22).padding(.bottom, 40).frame(maxWidth: .infinity)
            }
        }
    }

    private func feature(_ icon: String, _ title: LocalizedStringKey) -> some View {
        VStack(spacing: 8) { Image(systemName: icon).foregroundStyle(PortalTheme.gold); Text(title).font(.caption.weight(.semibold)).multilineTextAlignment(.center) }.frame(maxWidth: .infinity)
    }
}

struct ReauthenticationGateView: View {
    @Environment(AppModel.self) private var model
    let account: SiteAccount
    @State private var username: String
    @State private var password = ""
    @FocusState private var passwordFocused: Bool

    init(account: SiteAccount) {
        self.account = account
        _username = State(initialValue: account.username ?? "")
    }

    var body: some View {
        PortalBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(spacing: 14) {
                        PortalBrandMark(size: 54)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("login.again").font(.title.bold())
                            Text(account.siteName).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                        }
                    }

                    VStack(alignment: .leading, spacing: 18) {
                        Label("error.session.expired", systemImage: "person.badge.key.fill")
                            .font(.headline)
                            .foregroundStyle(PortalTheme.teal)

                        if let error = model.errorMessage {
                            PortalErrorBanner(message: error) { model.errorMessage = nil }
                        }

                        HStack(spacing: 12) {
                            Image(systemName: "person.fill").foregroundStyle(PortalTheme.teal).frame(width: 24)
                            TextField("login.username", text: $username)
                                .textContentType(.username)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                        }
                        .padding(14)
                        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))

                        HStack(spacing: 12) {
                            Image(systemName: "key.fill").foregroundStyle(PortalTheme.teal).frame(width: 24)
                            SecureField("login.password", text: $password)
                                .textContentType(.password)
                                .focused($passwordFocused)
                                .submitLabel(.go)
                                .onSubmit(signIn)
                        }
                        .padding(14)
                        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))

                        Button(action: signIn) {
                            HStack {
                                if model.isLoading { ProgressView().tint(.white) }
                                Text("login.action")
                                Spacer()
                                Image(systemName: "arrow.right")
                            }
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 52)
                        }
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.roundedRectangle(radius: 16))
                        .tint(PortalTheme.teal)
                        .disabled(model.isLoading || username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)

                        Text("onboarding.secure.body")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(22)
                    .portalCard()
                }
                .frame(maxWidth: 520)
                .padding(24)
                .frame(maxWidth: .infinity)
            }
        }
        .onAppear { passwordFocused = username.isEmpty == false }
        .accessibilityIdentifier("reauthentication.screen")
    }

    private func signIn() {
        guard !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !password.isEmpty else { return }
        let submittedPassword = password
        password = ""
        Task { await model.reauthenticate(username: username, password: submittedPassword) }
    }
}

struct AddSiteView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var siteURL = ""
    @State private var username = ""
    @State private var password = ""
    @FocusState private var focus: Field?
    enum Field { case url, username, password }

    var body: some View {
        PortalBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(spacing: 14) {
                        PortalBrandMark(size: 52)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(model.pendingConfig == nil ? "setup.title" : "login.title").font(.title.bold())
                            Text(model.pendingConfig?.siteName ?? String(localized: "setup.subtitle")).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }

                    if let error = model.errorMessage { PortalErrorBanner(message: error) { model.errorMessage = nil } }

                    if let config = model.pendingConfig { loginForm(config) }
                    else { siteForm }

                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "lock.shield.fill").foregroundStyle(PortalTheme.teal)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("onboarding.secure.title").font(.subheadline.weight(.semibold))
                            Text("onboarding.secure.body").font(.caption).foregroundStyle(.secondary)
                        }
                    }.padding(16).background(PortalTheme.teal.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                }.frame(maxWidth: 520).padding(24).frame(maxWidth: .infinity)
            }
        }
        .navigationTitle(Text("setup.navigation"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("action.cancel") { model.pendingConfig = nil; dismiss() } } }
        .interactiveDismissDisabled(model.isLoading)
    }

    private var siteForm: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("setup.description").font(.body).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 8) {
                Text("setup.url").font(.subheadline.weight(.semibold))
                TextField("setup.url.placeholder", text: $siteURL).textContentType(.URL).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($focus, equals: .url).submitLabel(.go).onSubmit(connect)
                    .padding(14).background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
            }
            Button(action: connect) {
                HStack { if model.isLoading { ProgressView().tint(.white) }; Text("setup.continue"); Spacer(); Image(systemName: "arrow.right") }
                    .font(.headline).frame(maxWidth: .infinity).frame(minHeight: 52)
            }.buttonStyle(.borderedProminent).buttonBorderShape(.roundedRectangle(radius: 16)).tint(PortalTheme.teal).disabled(model.isLoading || siteURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }.padding(22).portalCard()
    }

    private func loginForm(_ config: MoodlePublicConfig) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                PortalStatusPill(text: config.connectionMode == .nativeApi ? String(localized: "mode.api") : String(localized: "mode.html"), systemImage: config.connectionMode == .nativeApi ? "bolt.horizontal.circle.fill" : "doc.text.magnifyingglass", emphasized: true)
                Spacer(); Button("action.change_site") { model.pendingConfig = nil; password = "" }.font(.subheadline)
            }
            if config.browserSSORequired {
                Button { Task { await model.loginWithSSO(); if model.activeAccount != nil { dismiss() } } } label: {
                    Label("login.sso", systemImage: "person.badge.key.fill").font(.headline).frame(maxWidth: .infinity).frame(minHeight: 52)
                }.buttonStyle(.borderedProminent).buttonBorderShape(.roundedRectangle(radius: 16)).tint(PortalTheme.teal).disabled(model.isLoading)
            }
            if config.showLoginForm {
                VStack(spacing: 13) {
                    field("login.username", text: $username, icon: "person.fill", secure: false, field: .username)
                    field("login.password", text: $password, icon: "key.fill", secure: true, field: .password)
                }
                Button { signIn() } label: {
                    HStack { if model.isLoading { ProgressView().tint(.white) }; Text("login.action"); Spacer(); Image(systemName: "arrow.right") }.font(.headline).frame(maxWidth: .infinity).frame(minHeight: 52)
                }.buttonStyle(.borderedProminent).buttonBorderShape(.roundedRectangle(radius: 16)).tint(PortalTheme.teal).disabled(model.isLoading || username.isEmpty || password.isEmpty)
            } else if !config.browserSSORequired {
                Text("error.sso.html").foregroundStyle(.secondary)
            }
        }.padding(22).portalCard()
    }

    private func field(_ label: LocalizedStringKey, text: Binding<String>, icon: String, secure: Bool, field: Field) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundStyle(PortalTheme.teal).frame(width: 24)
            if secure { SecureField(label, text: text).textContentType(.password) }
            else { TextField(label, text: text).textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled() }
        }.focused($focus, equals: field).padding(14).background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
    }

    private func connect() { focus = nil; Task { await model.inspectSite(siteURL) } }
    private func signIn() { focus = nil; Task { await model.login(username: username, password: password); password = ""; if model.activeAccount != nil && model.pendingConfig == nil { dismiss() } } }
}

struct InitialAvatar: View {
    var name: String
    var size: CGFloat = 46
    var body: some View {
        Text(String(name.trimmingCharacters(in: .whitespacesAndNewlines).first ?? "M").uppercased())
            .font(.system(size: size * 0.36, weight: .bold, design: .rounded)).foregroundStyle(.white)
            .frame(width: size, height: size).background(LinearGradient(colors: [PortalTheme.teal, PortalTheme.navy], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
            .accessibilityLabel(name)
    }
}
