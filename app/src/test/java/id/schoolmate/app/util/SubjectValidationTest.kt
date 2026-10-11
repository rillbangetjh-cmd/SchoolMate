package id.schoolmate.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectValidationTest {
    @Test fun `blank name is rejected`() = assertEquals(SubjectNameError.Empty, validateSubjectName("   "))

    @Test fun `overlong name is rejected`() =
        assertEquals(SubjectNameError.TooLong, validateSubjectName("a".repeat(MAX_SUBJECT_NAME_LENGTH + 1)))

    @Test fun `valid name passes after trimming`() = assertNull(validateSubjectName("  Matematika  "))
}
