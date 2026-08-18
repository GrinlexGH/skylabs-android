package org.grinlexstudios.skylabs

import org.libsdl.app.SDLActivity

class SkylabsActivity : SDLActivity() {
    override fun getLibraries(): Array<String> {
        return arrayOf(
            "SDL3",
            // "SDL3_image",
            // "SDL3_mixer",
            // "SDL3_net",
            // "SDL3_ttf",
            "launcher"
        )
    }
}