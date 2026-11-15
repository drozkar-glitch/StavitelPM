import SwiftUI
import ComposeApp

struct HlavniCompose Kontejner: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct StavitelPmIosVstup: App {
    var body: some Scene {
        WindowGroup {
            HlavniComposeKontejner()
                .ignoresSafeArea(.all)
        }
    }
}
