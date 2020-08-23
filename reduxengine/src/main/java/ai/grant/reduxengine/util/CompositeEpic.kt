package ai.grant.reduxengine.util

import ai.grant.reduxengine.core.Action
import ai.grant.reduxengine.core.Epic
import ai.grant.reduxengine.core.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

@ExperimentalCoroutinesApi
class CompositeEpic<S : State>(private val epics: List<Epic<S>>): Epic<S> {
    override fun map(action: Action, state: S): Flow<Action> =
        epics.fold(emptyFlow()) { acc, epic -> merge(acc, epic.map(action, state)) }
}