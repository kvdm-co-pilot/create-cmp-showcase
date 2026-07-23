import SwiftUI
import ComposeApp
import FirebaseCore

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Firebase native init (GitLive wraps the native SDK; reads GoogleService-Info.plist).
        // MUST run before doInitKoin(), which wires the GitLive emulators.
        FirebaseApp.configure()
        // Koin initialisation for iOS.
        KoinHelperKt.doInitKoin()
        return true
    }
}
