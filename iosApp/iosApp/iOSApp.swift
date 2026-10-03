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
        // Koin initialisation for iOS.
        // Firebase native init (reads GoogleService-Info.plist). MUST run before doInitKoin(),
        // which points the GitLive clients at the local emulators in a debug build.
        FirebaseApp.configure()
        KoinHelperKt.doInitKoin()
        return true
    }
}
