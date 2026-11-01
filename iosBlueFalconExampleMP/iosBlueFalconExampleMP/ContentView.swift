import SwiftUI
import shared

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()           // Compose draws the background behind the status bar and home indicator
            .preferredColorScheme(.dark) // light status-bar text on the dark background
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
