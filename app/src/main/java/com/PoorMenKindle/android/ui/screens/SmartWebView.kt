package com.PoorMenKindle.android.ui.screens

import android.content.Context
import android.view.ActionMode
import android.webkit.WebView
open class SmartWebView(context: Context) : WebView(context) {
    var actionModeCallback: ActionMode.Callback? = null

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        // Use our custom callback if set, otherwise use the default
        return super.startActionMode(actionModeCallback ?: callback)
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        // Use our custom callback if set, otherwise use the default
        return super.startActionMode(actionModeCallback ?: callback, type)
    }
}

