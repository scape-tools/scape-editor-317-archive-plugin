package plugin

import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.concurrent.Task
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.image.ImageView
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import scape.editor.fs.RSArchive
import scape.editor.fs.RSFileStore
import scape.editor.gui.App
import scape.editor.gui.Settings
import scape.editor.gui.controller.BaseController
import scape.editor.util.HashUtils
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import java.util.*
import java.util.zip.CRC32

class Controller : BaseController() {

    @FXML
    lateinit var archiveTable: TableView<ArchiveModel>

    @FXML
    lateinit var archiveIconCol: TableColumn<ArchiveModel, ImageView>

    @FXML
    lateinit var archiveNameCol: TableColumn<ArchiveModel, String>

    @FXML
    lateinit var archiveIndexCol: TableColumn<ArchiveModel, Int>

    private val archiveData = FXCollections.observableArrayList<ArchiveModel>()

    @FXML
    lateinit var archiveTf: TextField

    @FXML
    lateinit var archiveEntryTable: TableView<ArchiveEntryModel>

    @FXML
    lateinit var archiveEntryIconCol: TableColumn<ArchiveEntryModel, ImageView>

    @FXML
    lateinit var archiveEntryFileCol: TableColumn<ArchiveEntryModel, Int>

    @FXML
    lateinit var archiveEntryNameCol: TableColumn<ArchiveEntryModel, String>

    @FXML
    lateinit var archiveEntrySizeCol: TableColumn<ArchiveEntryModel, String>

    @FXML
    lateinit var archiveEntryTf: TextField

    private val archiveEntryData = FXCollections.observableArrayList<ArchiveEntryModel>()

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        archiveIconCol.cellValueFactory = PropertyValueFactory("icon")
        archiveNameCol.setCellValueFactory { it.value.nameProperty }
        archiveIndexCol.setCellValueFactory { it.value.idProperty as ObservableValue<Int> }

        val filteredArchiveData = FilteredList(archiveData) { true}
        archiveTf.textProperty().addListener { _, _, newValue -> filteredArchiveData.setPredicate {
            if (newValue == null || newValue.isEmpty()) {
                return@setPredicate true
            }

            val lowercase = newValue.toLowerCase()

            if (it.name.toLowerCase().contains(lowercase) || it.id.toString().contains(lowercase)) {
                return@setPredicate true
            }

            return@setPredicate false
        }
        }

        val sortedArchiveList = SortedList(filteredArchiveData)
        sortedArchiveList.comparatorProperty().bind(archiveTable.comparatorProperty())
        archiveTable.items = sortedArchiveList

        archiveTable.selectionModel.selectedItemProperty().addListener { _, _ ,newValue ->

            newValue ?: return@addListener

            val archive = newValue.archive

            archiveEntryData.clear()

            for (i in 0 until archive.entryCount) {
                val entry = archive.entries[i]
                val model = ArchiveEntryModel(i, entry.hash, entry.data.size)

                Platform.runLater {
                    archiveEntryData.add(model)
                }
            }

        }

        archiveEntryIconCol.cellValueFactory = PropertyValueFactory("icon")
        archiveEntryFileCol.setCellValueFactory { it.value.idProperty as ObservableValue<Int> }
        archiveEntryNameCol.setCellValueFactory { it.value.nameProperty }
        archiveEntrySizeCol.setCellValueFactory { it.value.sizeProperty }

        val filteredArchiveEntryData = FilteredList(archiveEntryData) { true}
        archiveEntryTf.textProperty().addListener { _, _, newValue -> filteredArchiveEntryData.setPredicate {
            if (newValue == null || newValue.isEmpty()) {
                return@setPredicate true
            }

            val lowercase = newValue.toLowerCase()

            if (it.name.toLowerCase().contains(lowercase) || it.id.toString().contains(lowercase)) {
                return@setPredicate true
            }

            return@setPredicate false
        }
        }

        val sortedArchiveEntryList = SortedList(filteredArchiveEntryData)
        sortedArchiveEntryList.comparatorProperty().bind(archiveEntryTable.comparatorProperty())
        archiveEntryTable.items = sortedArchiveEntryList

        onPopulate()
    }

    @FXML
    private fun addEntry() {
        if (!App.fs.isLoaded) {
            return
        }

        val selected = archiveTable.selectionModel.selectedItem ?: return
        val archive = selected.archive

        val chooser = FileChooser()
        chooser.title = "Select a file to add"
        chooser.initialDirectory = File("./")
        val files = chooser.showOpenMultipleDialog(App.mainStage) ?: return

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {

                for (file in files) {
                    val data = Files.readAllBytes(file.toPath())

                    if (data.isEmpty()) {
                        continue
                    }

                    val index = archive.indexOf(file.name)

                    val model = ArchiveEntryModel(index, HashUtils.hashName(file.name), data.size)

                    Platform.runLater {
                        if (index != -1 && index < archiveEntryData.size) {
                            archiveEntryData[index] = model
                        } else {
                            archiveEntryData.add(model)
                        }
                    }

                    archive.writeFile(file.name, data)
                    Settings.putNameForHash(file.name)
                }

                return true
            }
        }

        task.run()

    }

    @FXML
    private fun replaceEntry() {
        if (!App.fs.isLoaded) {
            return
        }

        val selectedArchive = archiveTable.selectionModel.selectedItem ?: return
        val archive = selectedArchive.archive

        val chooser = FileChooser()
        chooser.title = "Select a file"
        chooser.initialDirectory = File("./")
        val selectedFile = chooser.showOpenDialog(App.mainStage) ?: return

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                val data = Files.readAllBytes(selectedFile.toPath())

                if (data.isEmpty()) {
                    return false
                }

                val index = archive.indexOf(selectedFile.name)

                if (index == -1) {
                    return false
                }

                archive.writeFile(selectedFile.name, data)
                Settings.putNameForHash(selectedFile.name)

                Platform.runLater {
                    if (index < archiveEntryData.size) {
                        archiveEntryData[index] = ArchiveEntryModel(index, HashUtils.hashName(selectedFile.name), data.size)
                    }
                }
                return true
            }
        }

        task.run()

    }

    @FXML
    private fun exportEntries() {
        if (!App.fs.isLoaded) {
            return
        }

        val selectedArchive = archiveTable.selectionModel.selectedItem ?: return
        val archive = selectedArchive.archive

        val chooser = DirectoryChooser()
        chooser.title = "Select directory to export to"
        chooser.initialDirectory = File("./")
        val dir = chooser.showDialog(App.mainStage) ?: return

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                var count = 0
                for (entry in archive.entries) {
                    val buffer = archive.readFile(entry.hash)
                    val name = Settings.getNameFromHash(entry.hash) ?: entry.hash.toString()

                    FileOutputStream(File(dir, name)).use { fos ->
                        Channels.newChannel(fos).write(buffer)
                    }

                    val progress = (count + 1).toDouble() / archive.entryCount * 100
                    updateMessage(String.format("%.2f%s", progress, "%"))
                    updateProgress((count + 1).toDouble(), archive.entryCount.toDouble())

                    count++
                }
                return true
            }
        }

        runTask("Export Archive Task", task)

    }

    @FXML
    private fun exportEntry() {
        if (!App.fs.isLoaded) {
            return
        }

        val chooser = DirectoryChooser()
        chooser.title = "Select directory to export to"
        chooser.initialDirectory = File("./")
        val dir = chooser.showDialog(App.mainStage) ?: return

        val selectedArchive = archiveTable.selectionModel.selectedItem ?: return
        val selectedEntry = archiveEntryTable.selectionModel.selectedItem ?: return

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                FileOutputStream(File(dir, selectedEntry.name)).use { fos ->
                    Channels.newChannel(fos).write(selectedArchive.archive.readFile(selectedEntry.name))
                }
                return true
            }
        }

        task.run()

    }

    @FXML
    private fun calculateChecksum() {
        if (!App.fs.isLoaded) {
            return
        }

        val selectedArchive = archiveTable.selectionModel.selectedItem ?: return
        val selectedEntry = archiveEntryTable.selectionModel.selectedItem ?: return

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                val crc = CRC32()

                val buffer = selectedArchive.archive.readFile(selectedEntry.name) ?: return false

                crc.update(buffer.array())
                val checksum = crc.value

                val alert = Alert(Alert.AlertType.INFORMATION)
                alert.title = "Computed Checksum"
                alert.headerText = "$checksum"

                Platform.runLater {
                    alert.show()
                }
                return true
            }
        }

        task.run()
    }

    @FXML
    private fun identifyHash() {
        if (!App.fs.isLoaded) {
            return
        }

        val input = TextInputDialog()
        input.title = "Input"
        input.headerText = "Name this entry"
        input.contentText = "Please enter a name:"
        val optional = input.showAndWait()

        if (!optional.isPresent) {
            return
        }

        val name = optional.get()
        val hash = HashUtils.hashName(name)

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                var found = false
                for (entry in archiveEntryData) {
                    if (entry.hash == hash && entry.name == entry.hash.toString()) {
                        entry.name = name
                        entry.nameProperty = SimpleStringProperty(name)
                        found = true
                    }
                }

                if (found) {
                    val alert = Alert(Alert.AlertType.INFORMATION)
                    alert.title = "Info"
                    alert.headerText = "Found!"
                    alert.contentText = "$hash = $name"

                    Platform.runLater {
                        archiveEntryTable.refresh()
                        alert.show()
                    }
                    Settings.putNameForHash(name)
                }

                return true
            }
        }

        task.run()

    }

    @FXML
    private fun renameEntry() {
        if (!App.fs.isLoaded) {
            return
        }

        val selectedArchive = archiveTable.selectionModel.selectedItem ?: return
        val selectedEntry = archiveEntryTable.selectionModel.selectedItem ?: return
        val archive = selectedArchive.archive

        val input = TextInputDialog()
        input.title = "Input"
        input.headerText = "Name this entry"
        input.contentText = "Please enter a name:"
        val optional = input.showAndWait()

        if (!optional.isPresent) {
            return
        }

        val result = optional.get()

        Settings.putNameForHash(result)

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                val buffer = archive.readFile(selectedEntry.hash)
                archive.rename(selectedEntry.hash, result)

                val pos = archiveEntryData.indexOf(selectedEntry)
                Platform.runLater {
                    archiveEntryData.remove(selectedEntry)
                    archiveEntryData.add(pos, ArchiveEntryModel(selectedEntry.id, HashUtils.hashName(result), buffer.capacity()))
                }
                return true
            }
        }

        task.run()
    }

    @FXML
    private fun removeEntry() {
        if (!App.fs.isLoaded) {
            return
        }

        val selectedArchive = archiveTable.selectionModel.selectedItem ?: return
        val selectedEntry = archiveEntryTable.selectionModel.selectedItem ?: return
        val archive = selectedArchive.archive

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.headerText = "Are you sure you want to delete this entry?"

        val optional = alert.showAndWait()

        if (!optional.isPresent) {
            return
        }

        if (optional.get() != ButtonType.OK) {
            return
        }

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                val name = selectedEntry.name

                var hash = -1
                try {
                    hash = name.toInt()
                } catch (ex: Exception) {

                }

                val result: Boolean

                if (hash == -1) {
                    result = archive.remove(selectedEntry.name)
                } else {
                    result = archive.remove(hash)
                }

                if (result) {
                    Platform.runLater {
                        archiveEntryData.remove(selectedEntry)
                    }
                }
                return true
            }
        }

        task.run()

    }

    @FXML
    private fun goBack() {
        switchScene("StoreScene")
    }

    @FXML
    private fun pack() {
        if (!App.fs.isLoaded) {
            return
        }

        val selectedArchive = archiveTable.selectionModel.selectedItem ?: return

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                val archive = selectedArchive.archive
                val store = App.fs.getStore(RSFileStore.ARCHIVE_FILE_STORE) ?: return false
                val encoded = archive.encode() ?: return false

                try {
                    if (store.writeFile(selectedArchive.id, encoded)) {
                        val alert = Alert(Alert.AlertType.INFORMATION)
                        alert.title = "Info"
                        alert.headerText = selectedArchive.name
                        alert.contentText = "Packed successfully!"
                        Platform.runLater {
                            alert.show()
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                return true
            }
        }

        task.run()
    }

    override fun onPopulate() {
        archiveData.clear()

        val task = object:Task<Boolean>() {
            override fun call(): Boolean {
                if (!App.fs.isLoaded) {
                    return false
                }

                val store = App.fs.getStore(RSFileStore.ARCHIVE_FILE_STORE) ?: return false

                for (file in 0 until store.fileCount) {
                    try {
                        val data = store.readFile(file) ?: continue

                        if (data.capacity() == 0) {
                            continue
                        }

                        val archive = RSArchive.decode(data) ?: continue

                        var name = Settings.getStoreEntryReferenceName(RSFileStore.ARCHIVE_FILE_STORE, file)

                        if (name == null) {
                            name = file.toString()
                        }

                        val model = ArchiveModel(file, name, archive)

                        Platform.runLater {
                            archiveData.add(model)
                        }

                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }

                return true
            }

        }

        task.run()
    }

    override fun onClear() {
        archiveData.clear()
        archiveEntryData.clear()
    }

}