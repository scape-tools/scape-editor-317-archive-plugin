package plugin

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.image.ImageView
import scape.editor.fs.RSArchive
import scape.editor.gui.Settings

class ArchiveModel(val id: Int, val name: String, val archive: RSArchive) {

    val idProperty = SimpleIntegerProperty(id)
    val nameProperty = SimpleStringProperty(name)
    val icon = ImageView(Settings.getIcon("dat_32.png"))
}