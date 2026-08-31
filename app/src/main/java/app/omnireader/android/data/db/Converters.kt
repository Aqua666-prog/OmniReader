package app.omnireader.android.data.db

import androidx.room.TypeConverter
import app.omnireader.android.core.model.ContentType
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.core.model.ReadStatus

class Converters {
    @TypeConverter fun fileFormatToString(value: FileFormat): String = value.name
    @TypeConverter fun stringToFileFormat(value: String): FileFormat = FileFormat.valueOf(value)
    @TypeConverter fun contentTypeToString(value: ContentType): String = value.name
    @TypeConverter fun stringToContentType(value: String): ContentType = ContentType.valueOf(value)
    @TypeConverter fun readStatusToString(value: ReadStatus): String = value.name
    @TypeConverter fun stringToReadStatus(value: String): ReadStatus = ReadStatus.valueOf(value)
}
