package ai.grant.reduxengine

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
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

class ReduxEngineTest {

    private val testIoDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
    private val testMainDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
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
    fun warmUp() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            stateChanges,
            reducer,
            epic,
            testMainDispatcher,
            testIoDispatcher
        )
        val listeningJob = launch {
            stateChanges.openSubscription().consumeEach {
                results.add(it)
            }
        }
        val initialState = ExampleState(0)

        // Execution
        subject.warmUp(initialState)

        // Post-conditions
        assertEquals(1, results.size)
        assertEquals(initialState, results[0])
        listeningJob.cancel()
    }

    @Test
    fun listen() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            stateChanges,
            reducer,
            epic,
            testMainDispatcher,
            testIoDispatcher
        )
        val initialState = ExampleState(0)
        val nextState = ExampleState(1)

        // Execution
        val listeningJob = launch {
            subject.listen {
                results.add(it)
            }
        }
        stateChanges.send(initialState)
        stateChanges.send(nextState)

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(nextState, results[1])
        listeningJob.cancel()
    }

    @Test
    fun dispatch() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            stateChanges,
            reducer,
            epic,
            testMainDispatcher,
            testIoDispatcher
        )
        val initialState = ExampleState(0)
        val listeningJob = launch {
            subject.listen {
                results.add(it)
            }
        }
        subject.warmUp(initialState)

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
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            stateChanges,
            reducer,
            epic,
            testMainDispatcher,
            testIoDispatcher
        )
        val initialState = ExampleState(0)
        val nextState = ExampleState(1)

        // Execution
        val listeningJob = launch {
            subject.stateChanges.consumeEach {
                results.add(it)
            }
        }
        subject.stateChanges.send(initialState)
        subject.stateChanges.send(nextState)

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(nextState, results[1])
        listeningJob.cancel()
    }

    @Test
    fun `Epic runs post Reducer and receives reduced state`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val testEpic = mockk<Epic<ExampleState>>()
        val stateSlot = slot<ExampleState>()
        val actionSlot = slot<Action>()
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            stateChanges,
            reducer,
            testEpic,
            testMainDispatcher,
            testIoDispatcher
        )
        val listeningJob = launch {
            subject.listen {
                results.add(it)
            }
        }
        subject.warmUp(initialState)
        every { testEpic.map(capture(actionSlot), capture(stateSlot)) } answers {
            emptyFlow<Action>()
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
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val testEpic = object: Epic<ExampleState> {
            override fun map(action: Action, state: ExampleState): Flow<Action> {
                val actions = ArrayList<Action>()
                if (action is ExampleAction.AddAction) {
                    actions.add(ExampleAction.SubtractAction(4))
                }
                return actions.asFlow()
            }
        }
        val subject: ReduxEngine<ExampleState> = ReduxEngine(
            stateChanges,
            reducer,
            testEpic,
            testMainDispatcher,
            testIoDispatcher
        )
        val listeningJob = launch {
            subject.listen {
                results.add(it)
            }
        }
        subject.warmUp(initialState)

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
