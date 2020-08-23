package ai.grant.reduxengine

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails

@ExperimentalCoroutinesApi
class ReduxEngineTest {

    private val testIoDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
    private val testMainDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
    private val testJob: Job = Job()
    private val epic: Epic<ExampleState> = ExampleEpic()
    private val reducer: Reducer<ExampleState> = ExampleReducer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testMainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        testMainDispatcher.cleanupTestCoroutines()
        testIoDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun listen() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            epic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val initialState = ExampleState(0)
        val nextState = ExampleState(1)

        // Execution
        subject.listen {
            results.add(it)
        }
        subject.stateChanges.send(initialState)
        subject.stateChanges.send(nextState)

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(nextState, results[1])
    }

    @Test
    fun dispatch() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel(initialState)
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            epic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val listeningJob = launch {
            stateChanges.openSubscription().consumeEach {
                results.add(it)
            }
        }

        // Execution
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(ExampleState(2), results[1])
        listeningJob.cancel()
    }

    @Test
    fun getStateChanges() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel(initialState)
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            epic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val nextState = ExampleState(1)

        // Execution
        subject.listen {
            results.add(it)
        }
        subject.stateChanges.send(initialState)
        subject.stateChanges.send(nextState)

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(nextState, results[1])
    }

    @Test
    fun `Most recent state is given when subscribing`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel(initialState)
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            epic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))
        val results = ArrayList<ExampleState>()

        // Execution
        subject.listen {
            results.add(it)
        }

        // Post-conditions
        assertEquals(1, results.size)
        assertEquals(ExampleState(4), results[0])
    }

    @Test
    fun `No more states received after cancelling subscription`() = runBlockingTest {
        // Pre-conditions
        val results: ArrayList<ExampleState> = ArrayList()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel(initialState)
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            epic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))

        // Execution
        val subscription = subject.listen {
            results.add(it)
        }
        subscription.cancel()
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(1, results.size)
    }

    @Test
    fun `ReduxEngine is off after cancelling job`() = runBlockingTest {
        // Pre-conditions
        val results: ArrayList<ExampleState> = ArrayList()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel(initialState)
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            epic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))

        // Execution
        val subscription = stateChanges.openSubscription()
        val listeningJob = launch {
            subscription.consumeEach {
                results.add(it)
            }
        }
        testJob.cancel()

        // Post-conditions
        assertFails {
            subject.dispatch(ExampleAction.AddAction(2))
        }
        assertEquals(1, results.size)
        listeningJob.cancel()
    }

    @Test
    fun `Epic runs post Reducer and receives reduced state`() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel(initialState)
        val testEpic = mockk<Epic<ExampleState>>()
        val stateSlot = slot<ExampleState>()
        val actionSlot = slot<Action>()
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            testEpic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val listeningJob = launch {
            stateChanges.openSubscription().consumeEach {
                results.add(it)
            }
        }
        every { testEpic.map(capture(actionSlot), capture(stateSlot)) } answers {
            emptyFlow()
        }

        // Execution
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(ExampleAction.AddAction(2), actionSlot.captured)
        assertEquals(ExampleState(2), stateSlot.captured)
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(ExampleState(2), results[1])
        listeningJob.cancel()
    }

    @Test
    fun `Epic maps action to action`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel(initialState)
        val testEpic = object : Epic<ExampleState> {
            override fun map(action: Action, state: ExampleState): Flow<Action> {
                val actions = ArrayList<Action>()
                if (action is ExampleAction.AddAction) {
                    actions.add(ExampleAction.SubtractAction(4))
                }
                return actions.asFlow()
            }
        }
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            reducer,
            testEpic,
            testJob,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val listeningJob = launch {
            stateChanges.openSubscription().consumeEach {
                results.add(it)
            }
        }

        // Execution
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(3, results.size)
        assertEquals(initialState, results[0])
        assertEquals(ExampleState(2), results[1])
        assertEquals(ExampleState(-2), results[2])
        listeningJob.cancel()
    }

    data class ExampleState(
        val value: Int
    ) : State

    sealed class ExampleAction : Action {
        data class AddAction(
            val value: Int
        ) : ExampleAction()

        data class SubtractAction(
            val value: Int
        ) : ExampleAction()
    }

    class ExampleReducer : Reducer<ExampleState> {
        override fun reduce(action: Action, state: ExampleState): ExampleState {
            return when (action) {
                is ExampleAction.AddAction -> {
                    state.copy(
                        value = state.value + action.value
                    )
                }
                is ExampleAction.SubtractAction -> {
                    state.copy(
                        value = state.value - action.value
                    )
                }
                else -> {
                    state
                }
            }
        }
    }

    class ExampleEpic : Epic<ExampleState> {
        override fun map(action: Action, state: ExampleState): Flow<Action> {
            return emptyFlow()
        }
    }
}
