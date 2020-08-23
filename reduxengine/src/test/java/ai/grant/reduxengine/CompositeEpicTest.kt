package ai.grant.reduxengine

import ai.grant.reduxengine.core.Action
import ai.grant.reduxengine.core.Epic
import ai.grant.reduxengine.core.State
import ai.grant.reduxengine.util.CompositeEpic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class CompositeEpicTest {
    @Test
    fun map() = runBlockingTest {
        // Pre-conditions
        val initialState = TestState(emptyList())
        val subject = CompositeEpic<TestState>(listOf(
            Epic { _, _ -> flowOf(TestActionTwo) },
            Epic { _, _ -> flowOf(TestActionThree) },
            Epic { _, _ -> flowOf(TestActionFour) }
        ))

        // Execution
        val result = subject.map(TestAction, initialState)

        // Post-conditions
        val list = ArrayList<Action>()
        result.collect {
            list.add(it)
        }
        assertEquals(list, listOf(TestActionTwo, TestActionThree, TestActionFour))
    }

    data class TestState(val value: List<Int>) : State

    object TestAction : Action

    object TestActionTwo : Action

    object TestActionThree : Action

    object TestActionFour : Action
}