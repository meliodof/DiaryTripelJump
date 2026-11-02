package com.example.diarytripeljump

import com.example.diarytripeljump.data.AgeGroups
import org.junit.Assert.assertEquals
import org.junit.Test

class AgeGroupsTest {

    @Test
    fun groupsByAgeInYear() {
        assertEquals("U12", AgeGroups.forAgeInYear(10, null).code)
        assertEquals("U14", AgeGroups.forAgeInYear(13, null).code)
        assertEquals("U16", AgeGroups.forAgeInYear(14, null).code)
        assertEquals("U18", AgeGroups.forAgeInYear(16, AgeGroups.GENDER_MALE).code)
        assertEquals("U20", AgeGroups.forAgeInYear(19, AgeGroups.GENDER_FEMALE).code)
        assertEquals("U23", AgeGroups.forAgeInYear(22, null).code)
        assertEquals("Мужчины", AgeGroups.forAgeInYear(23, AgeGroups.GENDER_MALE).code)
        assertEquals("Женщины", AgeGroups.forAgeInYear(30, AgeGroups.GENDER_FEMALE).code)
        assertEquals("Взрослые", AgeGroups.forAgeInYear(30, null).code)
    }
}
