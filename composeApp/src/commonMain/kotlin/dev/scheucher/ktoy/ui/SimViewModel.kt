package dev.scheucher.ktoy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.scheucher.ktoy.core.AssemblyResult
import dev.scheucher.ktoy.core.Example
import dev.scheucher.ktoy.core.ExampleKind
import dev.scheucher.ktoy.core.ListingLine
import dev.scheucher.ktoy.core.Machine
import dev.scheucher.ktoy.core.assemble
import dev.scheucher.ktoy.core.parseToyo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the running Machine and the derived UI state for the simulator screen.
 * Being a ViewModel, it survives Android configuration changes (rotation), so
 * the program keeps its ACC/PC/step state when the phone is turned — matching
 * the spec's "rotation must not reset" rule.
 *
 * A UI-visible [tick] counter is bumped on every state change so Compose
 * recomposes (Machine itself is plain mutable state, not observable).
 */
class SimViewModel : ViewModel() {

    var machine: Machine? = null; private set
    var listing: List<ListingLine> = emptyList(); private set
    var syms: Map<String, Int> = emptyMap(); private set
    var hasSource: Boolean = true; private set
    var title: String = ""; private set

    // bumped on every mutation so composables that read it recompose
    var tick by mutableIntStateOf(0); private set

    var running by mutableStateOf(false); private set
    var stepsPerSecond by mutableStateOf(2.0)

    // the auto-run coroutine, kept so it can be cancelled the instant any other
    // action touches the machine (load / reset / manual step / edit) — otherwise
    // a loop sitting in delay() could wake and step a just-swapped machine.
    private var runJob: Job? = null

    private fun bump() { tick += 1 }

    /** Stop auto-run: cancel the loop and clear the flag. Safe to call anytime. */
    private fun stopRun() {
        runJob?.cancel()
        runJob = null
        running = false
    }

    /** True once any program (or the empty image) has been loaded. */
    val hasProgram: Boolean get() = machine != null

    /**
     * Start with an empty, zeroed 256-byte memory and no source — like pytoy's
     * empty_gui(). The user then loads an example via the Load button.
     */
    fun loadEmpty() {
        stopRun()
        title = "(empty)"
        val mem = IntArray(256)
        listing = (0..255).map { ListingLine(it, listOf(mem[it]), "", true) }
        syms = emptyMap()
        hasSource = false
        machine = Machine(mem, (0..255).toSet())
        bump()
    }

    /** Load an example (ASM is assembled; TOYO is parsed as a byte image). */
    fun load(example: Example) {
        stopRun()
        title = example.name
        if (example.kind == ExampleKind.TOYO) {
            val mem = parseToyo(example.source)
            listing = (0..255).map { ListingLine(it, listOf(mem[it]), "", true) }
            syms = emptyMap()
            hasSource = false
            machine = Machine(mem, (0..255).toSet())
        } else {
            val res: AssemblyResult = assemble(example.source)
            listing = res.listing
            syms = res.syms
            hasSource = true
            machine = Machine(res.mem, res.dataAddrs)
        }
        bump()
    }

    fun step() {
        stopRun()
        machine?.step()
        memSelectedAddr = null   // selection no longer current after a step
        bump()
    }

    fun stepBack() {
        stopRun()
        machine?.stepBack()
        memSelectedAddr = null
        bump()
    }

    fun reset() {
        stopRun()
        machine?.reset()
        memSelectedAddr = null
        bump()
    }

    // The address selected specifically in the memory (CPU & memory) panel, or
    // null. Distinct from the machine's selectedAddr (which a source-line tap
    // also sets) so the Edit button is enabled only for a memory selection.
    var memSelectedAddr: Int? = null; private set

    /** Select an address. [fromMemory] marks a tap in the memory panel, which
     *  enables the Edit button; a source-line tap clears that. */
    fun select(addr: Int?, fromMemory: Boolean = false) {
        machine?.select(addr)
        memSelectedAddr = if (fromMemory) addr else null
        bump()
    }

    fun editCell(addr: Int, value: Int) {
        stopRun()
        machine?.editCell(addr, value)
        bump()
    }

    fun toggleRun() {
        if (running) {
            stopRun()
            return
        }
        val m = machine ?: return
        if (m.stopped) return
        running = true
        runJob = viewModelScope.launch {
            while (isActive) {
                val advanced = m.step()   // capture m: never step a swapped machine
                bump()
                if (!advanced) { running = false; runJob = null; break }
                val sps = stepsPerSecond.coerceIn(0.1, 1000.0)
                delay((1000.0 / sps).toLong())
            }
        }
    }
}
