//
//  ShiftclaApp.swift
//  Shiftcla
//
//  App 入口。
//

import SwiftUI

@main
struct ShiftclaApp: App {
    var body: some Scene {
        WindowGroup {
            #if DEBUG
            // 调试入口：`-glassDemo` 进入液态玻璃 demo
            if ProcessInfo.processInfo.arguments.contains("-glassDemo") {
                if #available(iOS 26.0, *) {
                    GlassLiquidDemo()
                } else {
                    Text("液态玻璃需要 iOS 26+")
                }
            } else {
                ContentView()
            }
            #else
            ContentView()
            #endif
        }
    }
}
