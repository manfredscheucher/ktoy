/*
 * Temporary workaround for [KT-80582](https://youtrack.jetbrains.com/issue/KT-80582)
 * 
 * This file should be safe to be removed once the ticket is closed and the project is updated to Kotlin version which solves that issue.
 */
config.watchOptions = config.watchOptions || {
    ignored: ["**/*.kt", "**/node_modules"]
}

if (config.devServer) {
    config.devServer.static = config.devServer.static.map(file => {
        if (typeof file === "string") {
            return {
                directory: file,
                watch: false,
            }
        } else {
            return file
        }
    })
}
// runWeb.sh / testWeb.sh open Chrome themselves (phone-sized window, or a fresh
// --user-data-dir profile for testWeb). Stop webpack from auto-opening a browser.
// Fixed dev-server port per app (see ~/github/.dev-ports.md) so apps started at
// the same time don't collide. ktoy = 8604.
if (config.devServer) {
    config.devServer.open = false
    config.devServer.port = 8604
}
