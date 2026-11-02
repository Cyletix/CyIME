// swift-tools-version: 6.1
import PackageDescription

let package = Package(
    name: "CyAzooKey",
    products: [
        .library(name: "CyAzooKey", type: .dynamic, targets: ["CyAzooKey"]),
    ],
    dependencies: [
        .package(path: "../akkc-lite"),
    ],
    targets: [
        .target(
            name: "CyAzooKey",
            dependencies: [.product(name: "KanaKanjiConverterModule", package: "akkc-lite")]
        ),
    ]
)
