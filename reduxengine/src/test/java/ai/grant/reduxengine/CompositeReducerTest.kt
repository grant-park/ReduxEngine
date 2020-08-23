package ai.grant.reduxengine

import ai.grant.reduxengine.core.Action
import ai.grant.reduxengine.core.Reducer
import ai.grant.reduxengine.core.State
import ai.grant.reduxengine.util.CompositeReducer
import org.junit.Test
import kotlin.test.assertEquals

class CompositeReducerTest {
    @Test
    fun reduce() {
        // Pre-conditions
        val initialState = TestState(emptyList())
        val subject = CompositeReducer<TestState>(
            listOf(
                Reducer { _, state -> state.copy(value = state.value + listOf(1)) },
                Reducer { _, state -> state.copy(value = state.value + listOf(2)) },
                Reducer { _, state -> state.copy(value = state.value + listOf(3)) }
            )
        )

        // Execution
        val result = subject.reduce(TestAction, initialState)

        // Post-conditions
        assertEquals(TestState(listOf(1, 2, 3)), result)
    }

    data class TestState(val value: List<Int>) : State

    object TestAction : Action
}