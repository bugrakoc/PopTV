package pm.koc.poptv.data.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.OffsetDateTime

class XmltvTimeTest {

    private fun utc(s: String) = OffsetDateTime.parse(s).toInstant().toEpochMilli()

    @Test
    fun `full timestamp with positive and negative offsets`() {
        assertThat(XmltvTime.parse("20240115203000 +0300")).isEqualTo(utc("2024-01-15T17:30:00Z"))
        assertThat(XmltvTime.parse("20240115203000 -0500")).isEqualTo(utc("2024-01-16T01:30:00Z"))
        assertThat(XmltvTime.parse("20240115203000 +0530")).isEqualTo(utc("2024-01-15T15:00:00Z"))
    }

    @Test
    fun `offset variants`() {
        val expected = utc("2024-01-15T17:30:00Z")
        assertThat(XmltvTime.parse("20240115203000+0300")).isEqualTo(expected)
        assertThat(XmltvTime.parse("20240115203000 +03:00")).isEqualTo(expected)
        assertThat(XmltvTime.parse("20240115173000 Z")).isEqualTo(expected)
        assertThat(XmltvTime.parse("20240115173000 UTC")).isEqualTo(expected)
        assertThat(XmltvTime.parse("20240115173000")).isEqualTo(expected) // no offset = UTC
        assertThat(XmltvTime.parse("  20240115173000 +0000  ")).isEqualTo(expected)
    }

    @Test
    fun `short forms and fractional seconds`() {
        assertThat(XmltvTime.parse("202401151730 +0000")).isEqualTo(utc("2024-01-15T17:30:00Z"))
        assertThat(XmltvTime.parse("2024011517 +0000")).isEqualTo(utc("2024-01-15T17:00:00Z"))
        assertThat(XmltvTime.parse("20240115")).isEqualTo(utc("2024-01-15T00:00:00Z"))
        assertThat(XmltvTime.parse("20240115173000.000 +0000")).isEqualTo(utc("2024-01-15T17:30:00Z"))
    }

    @Test
    fun `crosses day and year boundaries`() {
        assertThat(XmltvTime.parse("20240101010000 +0300")).isEqualTo(utc("2023-12-31T22:00:00Z"))
    }

    @Test
    fun `malformed values return null`() {
        assertThat(XmltvTime.parse(null)).isNull()
        assertThat(XmltvTime.parse("")).isNull()
        assertThat(XmltvTime.parse("2024")).isNull()
        assertThat(XmltvTime.parse("20241315000000 +0000")).isNull() // month 13
        assertThat(XmltvTime.parse("20240115250000 +0000")).isNull() // hour 25
        assertThat(XmltvTime.parse("20240115173000 +9900")).isNull()
        assertThat(XmltvTime.parse("20240115173000 EST")).isNull()
        assertThat(XmltvTime.parse("20240230120000 +0000")).isNull() // Feb 30
    }
}
