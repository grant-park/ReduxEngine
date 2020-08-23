package ai.grant.reduxenginesampleproject.reduxengine

import ai.grant.reduxengine.Action
import ai.grant.reduxengine.Epic
import ai.grant.reduxengine.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

@ExperimentalCoroutinesApi
class CompositeEpic<S : State>(private val epics: List<Epic<S>>): Epic<S> {
    override fun map(action: Action, state: S): Flow<Action> =
        epics.foldRight(emptyFlow()) { epic, acc -> merge(acc, epic.map(action, state)) }
}