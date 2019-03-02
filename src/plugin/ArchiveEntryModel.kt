package plugin

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.image.ImageView
import scape.editor.gui.Settings
import java.text.DecimalFormat

class ArchiveEntryModel(val id: Int, val hash: Int, val size: Int) {

    var name = Settings.getNameFromHash(hash) ?: hash.toString()
    val idProperty = SimpleIntegerProperty(id)
    var nameProperty = SimpleStringProperty(name)
    val sizeProperty = SimpleStringProperty(readableFileSize(size.toLong()))

    var icon = ImageView(if (name.endsWith(".idx")) Settings.getIcon("idx_32.png") else Settings.getIcon("dat_32.png"))

    private fun readableFileSize(size: Long): String {
        if (size <= 0) {
            return "0"
        }
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

}