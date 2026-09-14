package org.openscreentime.parent

import android.app.Application
import org.openscreentime.shared.repo.FamilyRepository

class ParentApp : Application() {
    val repository by lazy { FamilyRepository() }
}
