package org.bytefred.ktoy

import androidx.compose.ui.window.ComposeUIViewController
import org.bytefred.ktoy.ui.App

/**
 * The iOS entry point: wraps the shared Compose [App] in a UIViewController that
 * iosApp/ (ContentView.swift) embeds via UIViewControllerRepresentable. Mirrors
 * OpenYarnStash's MainViewController.
 */
@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController { App() }
