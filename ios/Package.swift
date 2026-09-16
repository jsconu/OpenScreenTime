// swift-tools-version: 5.9
import PackageDescription

/// OpenScreenTime Parent for iOS - see ios/README.md for what this is and the one-time
/// Xcode setup step needed to turn this into an installable app. This package holds all
/// the actual app logic and UI (models, the Firestore repository, SwiftUI screens) as a
/// library, so it can be compiled and checked in CI (see .github/workflows/ios-ci.yml)
/// without needing a full Xcode project.
let package = Package(
    name: "OpenScreenTimeParentKit",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "OpenScreenTimeParentKit", targets: ["OpenScreenTimeParentKit"])
    ],
    dependencies: [
        .package(url: "https://github.com/firebase/firebase-ios-sdk.git", from: "10.28.0")
    ],
    targets: [
        .target(
            name: "OpenScreenTimeParentKit",
            dependencies: [
                .product(name: "FirebaseAuth", package: "firebase-ios-sdk"),
                .product(name: "FirebaseFirestore", package: "firebase-ios-sdk"),
                .product(name: "FirebaseCrashlytics", package: "firebase-ios-sdk")
            ]
        ),
        .testTarget(
            name: "OpenScreenTimeParentKitTests",
            dependencies: ["OpenScreenTimeParentKit"]
        )
    ]
)
