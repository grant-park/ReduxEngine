package ai.grant.reduxengine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReduxEngineTest {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun `No reducers or epics`() {
        // Pre-conditions
        val firstState = object : State {}
        val subject = ReduxEngine(firstState)

        // Execution
        subject.dispatch(object : Action {})
    }

    @Test
    fun `Reducers run before epics`() = runBlockingTest {
        // Pre-conditions
        val firstState = object : State {}
        val subject = ReduxEngine(firstState)
        val history = ArrayList<State>()
        val job = launch {
            subject.stateChanges.asFlow().collect { state ->
                history.add(state)
            }
        }
        subject.addEpic { action, state ->
            arrayListOf(action)
        }
        subject.addReducer { state, action ->
            state
        }

        // Execution
        subject.dispatch(object : Action {})
        job.cancel()

        // Post-conditions
        assertEquals(history.size, 2)
    }
}
