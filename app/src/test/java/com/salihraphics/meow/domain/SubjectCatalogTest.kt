package com.salihraphics.meow.domain

import com.salihraphics.meow.domain.model.ExamType
import com.salihraphics.meow.domain.model.StudyProfile
import com.salihraphics.meow.domain.model.Subject
import com.salihraphics.meow.domain.model.SubjectCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectCatalogTest {

    @Test
    fun lgsOffersMiddleSchoolSubjectsOnly() {
        val lgs = SubjectCatalog.subjectsFor(ExamType.LGS)
        assertTrue(Subject.SCIENCE in lgs)
        assertTrue(Subject.REVOLUTION_HISTORY in lgs)
        assertFalse(Subject.PHYSICS in lgs)
    }

    @Test
    fun yksOffersSeparateSciences() {
        val yks = SubjectCatalog.subjectsFor(ExamType.YKS)
        assertTrue(Subject.PHYSICS in yks && Subject.CHEMISTRY in yks && Subject.BIOLOGY in yks)
        assertFalse(Subject.SCIENCE in yks)
    }

    @Test
    fun unspecifiedExamOffersEverything() {
        assertEquals(Subject.entries.toList(), SubjectCatalog.subjectsFor(null))
    }

    @Test
    fun switchingExamDropsSubjectsThatNoLongerApply() {
        val picked = setOf(Subject.MATH, Subject.PHYSICS, Subject.TURKISH)
        assertEquals(setOf(Subject.MATH, Subject.TURKISH), SubjectCatalog.retainAvailable(picked, ExamType.LGS))
    }

    @Test
    fun namesAreTrimmedCollapsedAndBlankMeansNoName() {
        assertEquals("Salih", StudyProfile.sanitizeName("  Salih  "))
        assertEquals("Ada Nur", StudyProfile.sanitizeName("Ada    Nur"))
        assertNull(StudyProfile.sanitizeName("   "))
        assertNull(StudyProfile.sanitizeName(null))
        assertEquals(StudyProfile.MAX_NAME_LENGTH, StudyProfile.sanitizeName("x".repeat(80))!!.length)
    }
}
