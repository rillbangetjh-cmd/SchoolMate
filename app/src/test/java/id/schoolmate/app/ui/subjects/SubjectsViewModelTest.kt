package id.schoolmate.app.ui.subjects

import id.schoolmate.app.testing.FakeSubjectRepository
import id.schoolmate.app.testing.MainDispatcherRule
import id.schoolmate.app.ui.screens.subjects.SubjectEditorError
import id.schoolmate.app.ui.screens.subjects.SubjectsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SubjectsViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @Test
    fun `adds subject and closes editor`() = runTest {
        val repo = FakeSubjectRepository()
        val vm = SubjectsViewModel(repo)
        backgroundScope.launch(mainRule.dispatcher) { vm.subjects.collect {} }

        vm.openEditor()
        vm.onNameChange("  Fisika ")
        vm.onTeacherChange("Pak Andi")
        vm.save()

        assertNull(vm.editor.value)
        assertEquals(listOf("Fisika"), vm.subjects.value?.map { it.name })
        assertEquals("Pak Andi", vm.subjects.value?.single()?.teacher)
    }

    @Test
    fun `empty name shows validation error`() = runTest {
        val vm = SubjectsViewModel(FakeSubjectRepository())
        vm.openEditor()
        vm.save()
        assertEquals(SubjectEditorError.EMPTY, vm.editor.value?.error)
    }

    @Test
    fun `duplicate name shows error and keeps editor open`() = runTest {
        val repo = FakeSubjectRepository()
        val vm = SubjectsViewModel(repo)
        vm.openEditor(); vm.onNameChange("Biologi"); vm.save()
        vm.openEditor(); vm.onNameChange("biologi"); vm.save()
        assertEquals(SubjectEditorError.DUPLICATE, vm.editor.value?.error)
        assertEquals(1, repo.subjects.value.size)
    }

    @Test
    fun `delete removes subject`() = runTest {
        val repo = FakeSubjectRepository()
        val vm = SubjectsViewModel(repo)
        vm.openEditor(); vm.onNameChange("Kimia"); vm.save()
        vm.delete(repo.subjects.value.single().id)
        assertEquals(0, repo.subjects.value.size)
    }
}
