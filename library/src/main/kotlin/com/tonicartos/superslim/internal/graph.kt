package com.tonicartos.superslim.internal

import android.support.v7.widget.RecyclerView
import android.util.SparseArray
import android.view.View
import com.tonicartos.superslim.*
import com.tonicartos.superslim.SectionConfig.Companion.GUTTER_AUTO
import com.tonicartos.superslim.internal.layout.FooterLayoutManager
import com.tonicartos.superslim.internal.layout.HeaderLayoutManager
import com.tonicartos.superslim.internal.layout.PaddingLayoutManager
import java.util.*

private const val ENABLE_FOOTER = true
private const val ENABLE_HEADER = false
private const val ENABLE_PADDING = false
private const val ENABLE_ITEM_CHANGE_LOGGING = false

internal class GraphManager(adapter: AdapterContract<*>) {
    val root: SectionState = adapter.getRoot().makeSection()
    private val sectionIndex = SectionManager()

    init {
        // Init root
        val rootId = sectionIndex.add(root)
        adapter.setRootId(rootId)
        // Init rest
        val adapterIds2SlmIds = adapter.getSections().mapValues { sectionIndex.add(it.value.makeSection()) }
        adapter.setSectionIds(adapterIds2SlmIds)

        val sectionData = SectionData()
        // Populate root
        adapter.populateRoot(sectionData)
        sectionData.subsections = sectionData.subsectionsById.map { sectionIndex[it] }
        sectionIndex[rootId].load(sectionData)
        // Populate rest
        adapterIds2SlmIds.forEach {
            adapter.populateSection(it.key to sectionData)
            sectionData.subsections = sectionData.subsectionsById.map { sectionIndex[it] }
            sectionIndex[it.value].load(sectionData)
        }
    }

    /*************************
     * Layout
     *************************/
    internal val anchor get() = root.anchor

    internal var requestedPositionOffset = 0
    internal var requestedPosition = 0
        get() = field
        set(value) {
            field = value
            hasRequestedPosition = true
        }
    private var hasRequestedPosition = false

    fun layout(helper: RootLayoutHelper) {
        if (!helper.isPreLayout) {
            //doSectionMoves()
            doSectionUpdates()
        }

        if (hasRequestedPosition) {
            // A little extra work to do it this way, but it is clearer and easier for now.
            root.resetLayout()
            root.setLayoutPositionFromAdapter(requestedPosition)
        }
        root.layout(helper, 0, 0, helper.layoutWidth)

        if (helper.isPreLayout) {
            doSectionRemovals()
        }

//        if (hasRequestedPosition && requestedPositionOffset != 0) {
//            scrollBy(requestedPositionOffset, helper)
//        }
    }

    fun scrollBy(d: Int, helper: RootLayoutHelper): Int {
        hasRequestedPosition = false
//        Log.d("Graph", "scrollBy($d)")
        if (d == 0) return 0
        // If d is +ve, then scrolling to end.
        return if (d > 0) scrollTowardsBottom(d, helper) else -scrollTowardsTop(-d, helper)
    }

    /**
     * Scroll down.
     *
     * @param dy Distance to scroll up by. Expects +ve value.
     * @param helper Root layout helper.
     *
     * @return Actual distance scrolled.
     */
    private fun scrollTowardsBottom(dy: Int, helper: RootLayoutHelper): Int {
        val scrolled = Math.min(dy, fillBottom(dy, helper))
        if (scrolled > 0) {
            helper.offsetChildrenVertical(-scrolled)
        }

        trimTop(scrolled, helper)

        return scrolled
    }

    /**
     * Scroll up.
     *
     * @param dy Distance to scroll up by. Expects +ve value.
     * @param helper Root layout helper.
     *
     * @return Actual distance scrolled.
     */
    private fun scrollTowardsTop(dy: Int, helper: RootLayoutHelper): Int {
        val scrolled = Math.min(dy, fillTop(dy, helper))
//        Log.d("scrollUp", "dy = $dy, scroll by = $scrolled")

        if (scrolled > 0) {
            helper.offsetChildrenVertical(scrolled)
        }

        trimBottom(scrolled, helper)

        return scrolled
    }

    private fun fillTop(dy: Int, helper: RootLayoutHelper) = root.fillTop(dy, helper)
    private fun fillBottom(dy: Int, helper: RootLayoutHelper) = root.fillBottom(dy, helper)
    private fun trimTop(scrolled: Int, helper: RootLayoutHelper)
            = root.trimTop(scrolled, helper, 0)

    private fun trimBottom(scrolled: Int, helper: RootLayoutHelper)
            = root.trimBottom(scrolled, helper, 0)

    /*************************
     * Scheduling section changes
     *************************/

    private data class ScheduledSectionRemoval(val section: Int, val parent: Int)

    private data class ScheduledSectionUpdate(val section: Int, val config: SectionConfig)

    //private data class ScheduledSectionMove(val section: Int, val fromParent: Int, val fromPosition: Int, val toParent: Int, val toPosition: Int)

    private val sectionsToRemove = arrayListOf<ScheduledSectionRemoval>()
    private val sectionsToUpdate = arrayListOf<ScheduledSectionUpdate>()
    //private val sectionsToMove = arrayListOf<ScheduledSectionMove>()

    fun sectionAdded(parent: Int, position: Int, config: SectionConfig): Int {
        val newSection = config.makeSection()
        sectionIndex[parent].insertSection(position, newSection)
        return sectionIndex.add(newSection)
    }

    fun queueSectionRemoved(section: Int, parent: Int) {
        sectionsToRemove.add(ScheduledSectionRemoval(section, parent))
    }

    fun queueSectionUpdated(section: Int, config: SectionConfig) {
        sectionsToUpdate.add(ScheduledSectionUpdate(section, config))
    }

    //fun queueSectionMoved(section: Int, fromParent: Int, fromPosition: Int, toParent: Int, toPosition: Int) {
    //    sectionsToMove.add(ScheduledSectionMove(section, fromParent, fromPosition, toParent, toPosition))
    //}

    private fun doSectionRemovals() {
        for (remove in sectionsToRemove) {
            sectionIndex[remove.parent].removeSection(sectionIndex[remove.section])
            sectionIndex.remove(remove.section)
        }
        sectionsToRemove.clear()
    }

    private fun doSectionUpdates() {
        for ((section, config) in sectionsToUpdate) {
            sectionIndex[section] = config.makeSection(sectionIndex[section])
        }
    }

    //private fun doSectionMoves() {
    //    for (move in sectionsToMove) {
    //        sections[move.fromParent).removeSection(move.fromPosition)
    //        sections[move.toParent).insertSection(move.toPosition, sections[move.section))
    //    }
    //    sectionsToMove.clear()
    //}

    /*************************
     * Item events
     *************************/
    fun addHeader(sectionId: Int) {
        sectionIndex[sectionId].addHeader()
    }

    fun removeHeader(sectionId: Int) {
        sectionIndex[sectionId].removeHeader()
    }

    fun addFooter(sectionId: Int) {
        sectionIndex[sectionId].addFooter()
    }

    fun removeFooter(sectionId: Int) {
        sectionIndex[sectionId].removeFooter()
    }

    fun addItems(sectionId: Int, childStart: Int, itemCount: Int) {
        if (itemCount == 0) return
        sectionIndex[sectionId].addItems(childStart, itemCount)
    }

    fun removeItems(sectionId: Int, childStart: Int, itemCount: Int) {
        if (itemCount == 0) return
        sectionIndex[sectionId].removeItems(childStart, itemCount)
    }

    fun moveItems(fromSection: Int, toSection: Int, from: Int, to: Int, itemCount: Int) {
        if (itemCount == 0) return
        sectionIndex[fromSection].removeItems(from, itemCount)
        sectionIndex[toSection].addItems(to, itemCount)
    }

    override fun toString(): String {
        return "$root"
    }
}

private class SectionManager {
    private var numSectionsSeen = 0
    private val sectionIndex = SparseArray<SectionState>()

    fun add(section: SectionState): Int {
        val id = numSectionsSeen
        numSectionsSeen += 1
        sectionIndex.put(id, section)
        return id
    }

    fun remove(section: Int) {
        sectionIndex.remove(section)
    }

    operator fun get(id: Int): SectionState = sectionIndex[id]

    operator fun set(id: Int, newSection: SectionState) {
        sectionIndex.put(id, newSection)
    }
}

/**
 * Section data
 */
abstract class SectionState(val baseConfig: SectionConfig, oldState: SectionState? = null) {
    private companion object {
        const val UNSET_OR_BEFORE_CHILDREN: Int = -1

        const val ENABLE_LAYOUT_LOGGING = false
    }

    internal inline fun <R> LayoutState.withPadding(helper: LayoutHelper, block: (Int, Int) -> R): R {
        val paddingTop: Int
        val paddingBottom: Int

        if (this is PaddingLayoutState) {
            paddingTop = helper.getTransformedPaddingTop(baseConfig)
            paddingBottom = helper.getTransformedPaddingBottom(baseConfig)
        } else {
            paddingTop = 0
            paddingBottom = 0
        }
        return block(paddingTop, paddingBottom)
    }

    internal inline fun <R> LayoutState.withPadding(helper: LayoutHelper, block: (Int, Int, Int, Int) -> R): R {
        val paddingLeft: Int
        val paddingTop: Int
        val paddingRight: Int
        val paddingBottom: Int

        if (this is PaddingLayoutState) {
            paddingLeft = helper.getTransformedPaddingLeft(baseConfig)
            paddingTop = helper.getTransformedPaddingTop(baseConfig)
            paddingRight = helper.getTransformedPaddingRight(baseConfig)
            paddingBottom = helper.getTransformedPaddingBottom(baseConfig)
        } else {
            paddingLeft = 0
            paddingTop = 0
            paddingRight = 0
            paddingBottom = 0
        }
        return block(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    open class LayoutState {
        /**
         * Number of views.
         */
        var numViews = 0
            internal set(value) {
                field = value
//                Log.d("add view", "value = $value, $this")
            }

        /**
         * The height of the section for this layout pass. Only valid after section is laid out.
         */
        open var bottom = 0

        /**
         * Position that is the head of the displayed section content. -1 is the unset default value.
         */
        var headPosition = UNSET_OR_BEFORE_CHILDREN
            set(value) {
                field = value
            }
        /**
         * Position that is the tail of the displayed section content. -1 is the unset and default value.
         */
        var tailPosition = UNSET_OR_BEFORE_CHILDREN

        var left = 0
        var right = 0

        /**
         * Area drawn past y0 and dy.
         */
        var overdraw = 0

        /**
         * Reset layout state.
         */
        internal open fun reset() {
            bottom = 0
            headPosition = UNSET_OR_BEFORE_CHILDREN
            tailPosition = UNSET_OR_BEFORE_CHILDREN
            left = 0
            right = 0
            overdraw = 0
            numViews = 0
        }

        internal fun copy(old: LayoutState) {
            bottom = old.bottom
            headPosition = old.headPosition
            tailPosition = old.tailPosition
        }

        override fun toString() = "($string)"

        protected open val string get() = "headPosition = $headPosition, tailPosition = $tailPosition, numViews = $numViews, left = $left, right = $right, height = $bottom, overdraw = $overdraw"

        open internal fun fillBottom(dy: Int, helper: LayoutHelper, section: SectionState)
                = section.doFillBottom(dy, helper, this)

        open internal fun fillTop(dy: Int, helper: LayoutHelper, section: SectionState)
                = section.doFillTop(dy, helper, this)

        open internal fun trimTop(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = section.doTrimTop(scrolled, helper, this)

        open internal fun trimBottom(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = section.doTrimBottom(scrolled, helper, this)

        open internal fun layout(helper: LayoutHelper, section: SectionState) {
            section.doLayout(helper, this)
        }

        open fun atTop(section: SectionState) = section.isAtTop(this)
    }

    internal abstract class InternalLayoutState : LayoutState() {
        abstract fun anchor(section: SectionState): Pair<Int, Int>
    }

    internal abstract class HfCommonLayoutState : InternalLayoutState() {
        /**
         * The current state of the header or footer. Values are HLM/FLM implementation dependent.
         */
        var state = 0

        override fun reset() {
            super.reset()
            state = 0
        }

        override val string get() = "state = $state, ${super.string}"
    }

    internal class PaddingLayoutState : InternalLayoutState() {
        var paddingTop = 0

        override val string get() = "paddingTop = $paddingTop, ${super.string}"

        override fun anchor(section: SectionState) = if (overdraw > 0) {
            section.positionInAdapter to overdraw
        } else {
            section.anchor
        }

        override fun atTop(section: SectionState) = PaddingLayoutManager.isAtTop(section, this)

        override fun layout(helper: LayoutHelper, section: SectionState) {
            PaddingLayoutManager.onLayout(helper, section, this)
        }

        override fun fillBottom(dy: Int, helper: LayoutHelper, section: SectionState)
                = PaddingLayoutManager.onFillBottom(dy, helper, section, this)

        override fun fillTop(dy: Int, helper: LayoutHelper, section: SectionState)
                = PaddingLayoutManager.onFillTop(dy, helper, section, this)

        override fun trimTop(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = PaddingLayoutManager.onTrimTop(scrolled, helper, section, this)

        override fun trimBottom(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = PaddingLayoutManager.onTrimBottom(scrolled, helper, section, this)

        override fun toString() = "Padding ${super.toString()}"
    }

    internal class HeaderLayoutState : HfCommonLayoutState() {
        override fun anchor(section: SectionState) = if (section.hasHeader && headPosition == 0) {
            section.positionInAdapter to overdraw
        } else {
            section.anchor
        }

        override fun atTop(section: SectionState) = HeaderLayoutManager.isAtTop(section, this)

        override fun layout(helper: LayoutHelper, section: SectionState) {
            HeaderLayoutManager.onLayout(helper, section, this)
        }

        override fun fillBottom(dy: Int, helper: LayoutHelper, section: SectionState)
                = HeaderLayoutManager.onFillBottom(dy, helper, section, this)

        override fun fillTop(dy: Int, helper: LayoutHelper, section: SectionState)
                = HeaderLayoutManager.onFillTop(dy, helper, section, this)

        override fun trimTop(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = HeaderLayoutManager.onTrimTop(scrolled, helper, section, this)

        override fun trimBottom(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = HeaderLayoutManager.onTrimBottom(scrolled, helper, section, this)

        override fun toString() = "Header ${super.toString()}"
    }

    internal class FooterLayoutState : HfCommonLayoutState() {
        override fun anchor(section: SectionState) = if (section.hasFooter && headPosition == 1) {
            section.positionInAdapter + section.totalItems to overdraw
        } else {
            section.anchor
        }

        override fun atTop(section: SectionState) = FooterLayoutManager.isAtTop(section, this)

        override fun layout(helper: LayoutHelper, section: SectionState) {
            FooterLayoutManager.onLayout(helper, section, this)
        }

        override fun fillBottom(dy: Int, helper: LayoutHelper, section: SectionState)
                = FooterLayoutManager.onFillBottom(dy, helper, section, this)

        override fun fillTop(dy: Int, helper: LayoutHelper, section: SectionState)
                = FooterLayoutManager.onFillTop(dy, helper, section, this)

        override fun trimTop(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = FooterLayoutManager.onTrimTop(scrolled, helper, section, this)

        override fun trimBottom(scrolled: Int, helper: LayoutHelper, section: SectionState)
                = FooterLayoutManager.onTrimBottom(scrolled, helper, section, this)

        override fun toString() = "Footer ${super.toString()}"
    }

    internal val anchor: Pair<Int, Int> get() =
    layoutState.babushka { state ->
        (state as? InternalLayoutState)
                ?.anchor(this@SectionState)
                ?: let { findAndWrap(state.headPosition, { it.anchor }, { it to state.overdraw }) }
    }

    override fun toString() = "Section: start=$positionInAdapter, totalItems=$totalItems, numChildren=$numChildren, numSections=${subsections.size}"

//    override fun toString() = subsections.foldIndexed(
//            "Section: start=$positionInAdapter, totalItems=$totalItems, numChildren=$numChildren, numSections=${subsections.size}, states = ${layoutState.asReversed().foldIndexed(
//                    "") { i, s, it ->
//                "$s\n$i $it".replace("\n", "\n\t")
//            }}") { i, s, it ->
//        "$s\n$i $it".replace("\n", "\n\t")
//    }

    /**
     * A stack of states. Plm, hlm, flm, slm. Except in special circumstances only the top one should be accessed at
     * a time.
     */
    private val layoutState = Stack<LayoutState>()
    internal val height get() = layoutState.peek().bottom
    internal val numViews get() = layoutState.peek().numViews
    internal fun resetLayout() {
        layoutState.forEach { it.reset() }
        subsections.forEach { it.resetLayout() }
    }

    /****************************************************
     * Section
     ****************************************************/

    /**
     * Total number of children. Children does not equate to items as some subsections may be empty.
     */
    var numChildren = 0
        private set(value) {
            field = value
        }

    /**
     * Total number of items in the section, including the header, footer, and items in subsections.
     */
    internal var totalItems = 0
        private set(value) {
            if (totalItemsAreImmutable) return
            parent?.itemCountsChangedInSubsection(this, value - field)
            field = value
        }

    /**
     * Ignore changes called in from subsections while true.
     */
    private var totalItemsAreImmutable = false

    internal var parent: SectionState? = null

    /**
     * Sorted list of subsections.
     */
    internal val subsections: ArrayList<SectionState>

    /**
     * Position of this section in the adapter.
     */
    internal var positionInAdapter = 0
        get() = field
        set(value) {
            subsections.forEach { it.positionInAdapter += value - field }
            field = value
        }

    init {
        layoutState.push(LayoutState())
        if (ENABLE_FOOTER) layoutState.push(FooterLayoutState())
        if (ENABLE_HEADER) layoutState.push(HeaderLayoutState())
        if (ENABLE_PADDING) layoutState.push(PaddingLayoutState())
        if (oldState != null) {
            layoutState[0].copy(oldState.layoutState[0])
            if (ENABLE_FOOTER) layoutState[1].copy(oldState.layoutState[1])
            if (ENABLE_HEADER) layoutState[2].copy(oldState.layoutState[2])
            if (ENABLE_PADDING) layoutState[3].copy(oldState.layoutState[3])
            totalItems = oldState.totalItems
            numChildren = oldState.numChildren
            subsections = oldState.subsections
            positionInAdapter = oldState.positionInAdapter
            parent = oldState.parent
        } else {
            subsections = ArrayList()
        }
    }

    /*************************
     * Access items
     *************************/

    internal var hasHeader = false
    internal var hasFooter = false

    internal fun getHeader(helper: LayoutHelper): Child? =
            if (hasHeader) {
                ItemChild.wrap(positionInAdapter, helper)
            } else {
                null
            }

    internal fun getFooter(helper: LayoutHelper): Child? =
            if (hasFooter) {
                val footerPositionInAdapter = positionInAdapter + totalItems - 1
                ItemChild.wrap(footerPositionInAdapter, helper)
            } else {
                null
            }

    internal fun getDisappearingHeader(helper: LayoutHelper): Child? =
            if (hasHeader && helper.scrapHasPosition(positionInAdapter)) {
                DisappearingItemChild.wrap(positionInAdapter, helper)
            } else {
                null
            }

    internal fun getDisappearingFooter(helper: LayoutHelper): Child? {
        val footerPositionInAdapter = positionInAdapter + totalItems - 1
        return if (hasFooter && helper.scrapHasPosition(footerPositionInAdapter)) {
            DisappearingItemChild.wrap(footerPositionInAdapter, helper)
        } else {
            null
        }
    }

    /**
     * Gets a child at the specified position that might need more layout.
     *
     * @return Null if the child at the position is known to be fully laid out.
     */
    internal fun getNonFinalChildAt(helper: LayoutHelper, position: Int): Child?
            = findAndWrap(position, { SectionChild.wrap(it, helper) }, { null })

    /**
     * Get the child at the position.
     */
    internal fun getChildAt(helper: LayoutHelper, position: Int): Child
            = findAndWrap(position, { SectionChild.wrap(it, helper) },
                          { ItemChild.wrap(it, helper) })

    /**
     * Get a disappearing child. This is a special child which is used to correctly position disappearing views. Any
     * views not in the scrap list are virtual and are not fetched from the recycler. Instead a dummy item is returned
     * which behaves as a view with layout params(width = match_parent, height = 0).
     */
    internal fun getDisappearingChildAt(helper: LayoutHelper, position: Int): Child
            = findAndWrap<Child>(position, { SectionChild(it, helper) }, {
        if (helper.scrapHasPosition(it)) {
            DisappearingItemChild.wrap(it, helper)
        } else {
            DummyChild.wrap(helper)
        }
    })

    inline private fun <T> findAndWrap(position: Int, wrapSection: (SectionState) -> T,
                                       wrapItem: (viewPosition: Int) -> T): T {
        var hiddenItems = positionInAdapter + if (hasHeader) 1 else 0
        var lastSectionPosition = 0

        for ((i, it) in subsections.withIndex()) {
            if (it.positionInAdapter - hiddenItems + i > position) {
                break
            } else if (it.positionInAdapter - hiddenItems + i == position) {
                return wrapSection(it)
            } else {
                hiddenItems += it.totalItems
                lastSectionPosition = i
            }
        }
        return wrapItem(hiddenItems + position - lastSectionPosition)
    }

    /*************************
     * Utility
     *************************/

    internal fun constrainToItemRange(position: Int): Int {
        return Math.max(position, totalItems - 1)
    }

    /*************************
     * Layout
     *************************/

    fun layout(helper: LayoutHelper, left: Int, top: Int, right: Int, numViewsBefore: Int = 0) {
//        Log.d("SECTION", "layout: left = $left, top = $top, right = $right, helper.viewsBefore = ${helper.viewsBefore}, viewsBefore = $numViewsBefore")
        if (totalItems == 0) {
            layoutState.peek().reset()
            return
        }

        layoutState.babushka { state ->
            state.withPadding(helper) { paddingLeft, paddingTop, paddingRight, paddingBottom ->
                state.left = left + paddingLeft
                state.right = right - paddingRight
                state.numViews = 0

                helper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom,
                                           helper.viewsBefore + numViewsBefore, state) { helper ->
                    state.layout(helper, this@SectionState)
                }
            }
        }
    }

    /**
     * First call into the root section. Comes from graph manager.
     */
    internal fun layout(rootHelper: RootLayoutHelper, left: Int, top: Int, right: Int) {
        if (totalItems == 0) {
            layoutState.peek().reset()
            return
        }

        // Use recycler view (base) padding.
        val paddingLeft = rootHelper.basePaddingLeft
        val paddingTop = rootHelper.basePaddingTop
        val paddingRight = rootHelper.basePaddingRight
        val paddingBottom = rootHelper.basePaddingBottom

        layoutState.babushka { state ->
            state.left = left + paddingLeft
            state.right = right - paddingRight
            state.numViews = 0

            if (state.headPosition == UNSET_OR_BEFORE_CHILDREN) state.headPosition = 0
            rootHelper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom, 0,
                                           state) { helper ->
                state.layout(helper, this@SectionState)
            }
        }
    }

    internal val atTop: Boolean get() = layoutState.babushka { it.atTop(this@SectionState) }

    fun isChildAtTop(position: Int) = findAndWrap(position, { it.atTop }, { true })

    /**
     * Set the layout position of this section.
     *
     * @return False if the position is not within this section.
     */
    internal fun setLayoutPositionFromAdapter(requestedAdapterPosition: Int): Boolean {
        // WARNING: Will crash if any disable Slms are true.
        // Check requested position is in this section.
        if (requestedAdapterPosition < positionInAdapter ||
                positionInAdapter + totalItems <= requestedAdapterPosition) {
            return false
        }
        layoutState.babushka { it.headPosition = 0 }

        // Check if position is header.
        if (hasHeader && requestedAdapterPosition == positionInAdapter) {
            layoutState.babushka {
                babushka { it.headPosition = 0 }
            }
            return true
        }

        // Check if position is footer.
        if (hasFooter && requestedAdapterPosition == positionInAdapter + totalItems - 1) {
            layoutState.babushka {
                babushka {
                    it.headPosition = 1
                    babushka { it.headPosition = 1 }
                }
            }
            return true
        }

        /*
         * Position is within content. It may be in a subsection or an item of this section. Calculating the child
         * position is a little difficult as it must account for interleaved child items and subsections.
         */
        val headerCount = if (hasHeader) 1 else 0
        var offset = positionInAdapter + headerCount
        var childrenAccountedFor = 0 + headerCount

        for ((_, section) in subsections.withIndex()) {
            if (requestedAdapterPosition < section.positionInAdapter) {
                // Position is before this subsection, so it must be a child item not a member of a subsection.
                layoutState.babushka {
                    babushka {
                        it.headPosition = 1
                        babushka {
                            it.headPosition = 0
                            babushka {
                                it.headPosition = childrenAccountedFor + requestedAdapterPosition - offset - headerCount
                            }
                        }
                    }
                }
                return true
            }

            // Add items before this subsection (but after the last subsection) to children count.
            childrenAccountedFor += section.positionInAdapter - offset

            if (section.setLayoutPositionFromAdapter(requestedAdapterPosition)) {
                // Requested position was within the subsection so store it as the layout position of this section.
                layoutState.babushka {
                    babushka {
                        it.headPosition = 1
                        babushka {
                            it.headPosition = 0
                            babushka {
                                it.headPosition = childrenAccountedFor - headerCount
                            }
                        }
                    }
                }
                return true
            }

            // Update offset to be after the subsection content.
            offset = section.positionInAdapter + section.totalItems
            // Account for this subsection.
            childrenAccountedFor += 1
        }


        // Position must be a child item after the last subsection.
        layoutState.babushka {
            babushka {
                it.headPosition = 1
                babushka {
                    it.headPosition = 0
                    babushka {
                        it.headPosition = childrenAccountedFor + requestedAdapterPosition - offset - headerCount
                    }
                }
            }
        }
        return true
    }

    internal infix operator fun contains(viewHolder: RecyclerView.ViewHolder): Boolean =
            viewHolder.layoutPosition >= positionInAdapter &&
                    viewHolder.layoutPosition < positionInAdapter + totalItems

    /*************************
     * Scrolling
     *************************/

    /****************
     * Root
     ****************/
    internal fun fillTop(dy: Int, rootHelper: RootLayoutHelper) =
            layoutState.babushka { state ->
                val paddingTop = rootHelper.basePaddingTop
                val paddingBottom = rootHelper.basePaddingBottom

                rootHelper.useSubsectionHelper(0, state.left, state.right, paddingTop, paddingBottom, 0,
                                               state) { helper ->
                    state.fillTop(dy, helper, this@SectionState)
                }
            }

    internal fun fillBottom(dy: Int, rootHelper: RootLayoutHelper) =
            layoutState.babushka { state ->
                val paddingTop = rootHelper.basePaddingTop
                val paddingBottom = rootHelper.basePaddingBottom

                rootHelper.useSubsectionHelper(0, state.left, state.right, paddingTop, paddingBottom, 0,
                                               state) { helper ->
                    state.fillBottom(dy, helper, this@SectionState)
                }
            }

    internal fun trimTop(scrolled: Int, rootHelper: RootLayoutHelper, top: Int) {
        layoutState.babushka { state ->
            val paddingTop = rootHelper.basePaddingTop
            val paddingBottom = rootHelper.basePaddingBottom

            rootHelper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom, 0,
                                           state) { helper ->
                state.trimTop(scrolled, helper, this@SectionState)
            }
        }
    }

    internal fun trimBottom(scrolled: Int, rootHelper: RootLayoutHelper, top: Int) {
        layoutState.babushka { state ->
            val paddingTop = rootHelper.basePaddingTop
            val paddingBottom = rootHelper.basePaddingBottom

            rootHelper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom, 0,
                                           state) { helper ->
                state.trimBottom(scrolled, helper, this@SectionState)
            }
        }
    }

    /***************
     * Sections
     ***************/
    internal fun fillTop(dy: Int, left: Int, top: Int, right: Int, helper: LayoutHelper, numViewsBefore: Int = 0) =
            layoutState.babushka { state ->
                state.withPadding(helper) { paddingLeft, paddingTop, paddingRight, paddingBottom ->
                    state.left = left + paddingLeft
                    state.right = right - paddingRight

                    helper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom,
                                               helper.viewsBefore + numViewsBefore, state) {
                        state.fillTop(dy, it, this@SectionState)
                    }
                }
            }

    internal fun fillBottom(dy: Int, left: Int, top: Int, right: Int, helper: LayoutHelper, numViewsBefore: Int = 0) =
            layoutState.babushka { state ->
                state.withPadding(helper) { paddingLeft, paddingTop, paddingRight, paddingBottom ->
                    state.left = left + paddingLeft
                    state.right = right - paddingRight

                    helper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom,
                                               helper.viewsBefore + numViewsBefore, state) {
                        state.fillBottom(dy, it, this@SectionState)
                    }
                }
            }

    internal fun trimTop(scrolled: Int, top: Int, helper: LayoutHelper, numViewsBefore: Int = 0) =
            layoutState.babushka { state ->
                state.withPadding(helper) { paddingTop, paddingBottom ->
                    helper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom,
                                               helper.viewsBefore + numViewsBefore, state) {
                        state.trimTop(scrolled, it, this@SectionState)
                    }
                }
            }

    internal fun trimBottom(scrolled: Int, top: Int, helper: LayoutHelper, numViewsBefore: Int = 0) =
            layoutState.babushka { state ->
                state.withPadding(helper) { paddingTop, paddingBottom ->
                    helper.useSubsectionHelper(top, state.left, state.right, paddingTop, paddingBottom,
                                               helper.viewsBefore + numViewsBefore, state) {
                        state.trimBottom(scrolled, it, this@SectionState)
                    }
                }
            }

    protected abstract fun isAtTop(layoutState: LayoutState): Boolean
    protected abstract fun doLayout(helper: LayoutHelper, layoutState: LayoutState)
    protected abstract fun doFillTop(dy: Int, helper: LayoutHelper, layoutState: LayoutState): Int
    protected abstract fun doFillBottom(dy: Int, helper: LayoutHelper, layoutState: LayoutState): Int
    protected abstract fun doTrimTop(scrolled: Int, helper: LayoutHelper, layoutState: LayoutState): Int
    protected abstract fun doTrimBottom(scrolled: Int, helper: LayoutHelper, layoutState: LayoutState): Int

    /*************************
     * Item management
     *************************/

    internal fun addHeader() {
        hasHeader = true
        subsections.forEach { it.positionInAdapter += 1 }
        totalItems += 1
    }

    internal fun addFooter() {
        hasFooter = true
        totalItems += 1
    }

    internal fun removeHeader() {
        hasHeader = false
        subsections.forEach { it.positionInAdapter -= 1 }
        totalItems -= 1
    }

    internal fun removeFooter() {
        hasFooter = false
        totalItems -= 1
    }

    internal fun addItems(position: Int, itemCount: Int) {
        var childPositionStart = position
        if (childPositionStart < 0) {
            childPositionStart = numChildren
        }

        applyToSubsectionsAfterChildPosition(childPositionStart) { _, it ->
            it.positionInAdapter += itemCount
        }

        numChildren += itemCount
        totalItems += itemCount
    }

    internal fun removeItems(fromAdapterPosition: Int, count: Int) {
        removeItemsInt(fromAdapterPosition, count)
    }
//        var currentRemoveFrom = fromAdapterPosition
//        var countInRange = count
//
//        // Constrain removal to range of items within the section.
//        if (currentRemoveFrom < positionInAdapter) {
//            val delta = positionInAdapter - currentRemoveFrom
//            countInRange -= delta
//            currentRemoveFrom += delta
//        }
//
//        if (currentRemoveFrom + countInRange > positionInAdapter + totalItems) {
//            val delta = (currentRemoveFrom + countInRange) - (positionInAdapter + totalItems)
//            countInRange -= delta
//        }
//
//        if (countInRange <= 0) return
//
//        var itemsRemaining = countInRange
//        var itemsThatAreChildren = 0
//
//        var itemsRemoved = 0
//
//        if (hasHeader && currentRemoveFrom == positionInAdapter) {
//            itemsRemoved += 1
//            itemsRemaining -= 1
//            currentRemoveFrom += 1
//            hasHeader = false
//        }
//
//        blockTotalItemChanges {
//            for (it in subsections) {
//                var (skipped, removed) = it.removeItemsInt(currentRemoveFrom, itemsRemaining)
//                it.positionInAdapter -= skipped + itemsRemoved
//                itemsRemoved += removed
//                itemsThatAreChildren += skipped
//                currentRemoveFrom += skipped
//                itemsRemaining -= removed + skipped
//            }
//        }
//
//        itemsThatAreChildren += itemsRemaining
//        totalItems -= countInRange
//        numChildren -= itemsThatAreChildren
//    }

    private fun removeItemsInt(removeFromAdapterPosition: Int, count: Int): Pair<Int, Int> {
        if (count == 0) return 0 to 0
        if (positionInAdapter + totalItems <= removeFromAdapterPosition) return 0 to 0 // Before removed items

        val itemsBeforeSection = Math.min(count, Math.max(0, positionInAdapter - removeFromAdapterPosition))
        if (positionInAdapter >= removeFromAdapterPosition + count) return itemsBeforeSection to 0 //After removed items

        val itemsAfterSection = Math.max(0, (removeFromAdapterPosition + count) - (positionInAdapter + totalItems))

        var currentRemoveFrom = Math.max(positionInAdapter, removeFromAdapterPosition)
        var itemsRemaining = count - itemsBeforeSection - itemsAfterSection
        var itemsThatAreChildren = 0
        var itemsRemoved = 0

        if (hasHeader && currentRemoveFrom == positionInAdapter) {
            itemsRemoved += 1
            itemsRemaining -= 1
            currentRemoveFrom += 1
            hasHeader = false
        }

        if (itemsRemaining == 0) {
            totalItems -= itemsRemoved
            return itemsBeforeSection to itemsRemoved
        }

        var removedInSection = 0
        blockTotalItemChanges {
            for (subsection in subsections) {
                val (before, removed) = subsection.removeItemsInt(currentRemoveFrom, itemsRemaining)
                removedInSection += before
                subsection.positionInAdapter -= removedInSection
                itemsThatAreChildren += before
                currentRemoveFrom += before
                itemsRemaining -= removed + before
                itemsRemoved += removed
                if (itemsRemaining == 0) break
            }
        }

        itemsThatAreChildren += itemsRemaining
        totalItems -= itemsRemoved + itemsThatAreChildren
        numChildren -= itemsThatAreChildren
        itemsRemoved += itemsThatAreChildren

        return itemsBeforeSection to itemsRemoved
    }

    private inline fun blockTotalItemChanges(f: () -> Unit) {
        totalItemsAreImmutable = true
        f()
        totalItemsAreImmutable = false
    }

    internal fun itemCountsChangedInSubsection(child: SectionState, changedCount: Int) {
        // Find child and adjust adapter position for sections after it.
        val index = subsections.indexOfFirst { it == child }
        if (index + 1 < subsections.size) {
            for (i in (index + 1)..(subsections.size - 1)) {
                subsections[i].positionInAdapter += changedCount
            }
        }

        totalItems += changedCount
    }

    /*************************
     * Section management
     *************************/

    internal fun insertSection(position: Int, newSection: SectionState) {
        var childInsertionPoint = position
        if (childInsertionPoint < 0) {
            childInsertionPoint = numChildren
        }

        var insertPoint = subsections.size
        var firstTime = true
        applyToSubsectionsAfterChildPosition(childInsertionPoint) { i, it ->
            it.positionInAdapter += newSection.totalItems
            if (firstTime) {
                insertPoint = i
                firstTime = false
            }
        }
        subsections.add(insertPoint, newSection)

        newSection.parent = this
        var numItemsInPriorSiblings = 0
        if (insertPoint > 0) {
            for (i in 0..(insertPoint - 1)) {
                numItemsInPriorSiblings += subsections[i].totalItems
            }
        }
        val priorChildrenThatAreItems = childInsertionPoint - insertPoint
        newSection.positionInAdapter = positionInAdapter + priorChildrenThatAreItems + numItemsInPriorSiblings
        numChildren += 1
        totalItems += newSection.totalItems
    }

    internal fun removeSection(section: SectionState) {
        var indexOfSection: Int = -1
        var afterSection = false
        subsections.forEachIndexed { i, it ->
            if (afterSection) {
                it.positionInAdapter -= section.totalItems
            } else if (it === section) {
                indexOfSection = i
                afterSection = true
            }
        }
        if (indexOfSection == -1) return

        subsections.removeAt(indexOfSection)
        totalItems -= section.totalItems
        numChildren -= 1
    }

    internal fun load(data: SectionData) {
        numChildren = data.childCount
        positionInAdapter = data.adapterPosition
        hasHeader = data.hasHeader
        totalItems = data.itemCount
        subsections.clear()
        subsections.addAll(data.subsections)
    }

    private inline fun applyToSubsectionsAfterChildPosition(childPositionStart: Int,
                                                            crossinline f: (Int, SectionState) -> Unit) {
        var hiddenItems = positionInAdapter
        var applying = false
        subsections.forEachIndexed { i, it ->
            if (applying) {
                f(i, it)
            } else if (it.positionInAdapter - hiddenItems + i >= childPositionStart) {
                f(i, it)
                applying = true
            } else {
                hiddenItems += it.totalItems
            }
        }
    }

    internal inline fun rightGutter(autoWidth: () -> Int)
            = if (baseConfig.gutterRight == GUTTER_AUTO) autoWidth() else baseConfig.gutterRight

    internal inline fun leftGutter(autoWidth: () -> Int)
            = if (baseConfig.gutterLeft == GUTTER_AUTO) autoWidth() else baseConfig.gutterLeft
}

internal abstract class ChildInternal(var helper: LayoutHelper) : Child {
    override fun trimTop(scrolled: Int, top: Int, helper: LayoutHelper, numViewsBefore: Int) = 0
    override fun trimBottom(scrolled: Int, top: Int, helper: LayoutHelper, numViewsBefore: Int) = 0
}

private open class SectionChild(var section: SectionState, helper: LayoutHelper) : ChildInternal(helper) {
    companion object {
        val pool = arrayListOf<SectionChild>()

        fun wrap(section: SectionState, helper: LayoutHelper): SectionChild {
            return if (pool.isEmpty()) {
                SectionChild(section, helper)
            } else {
                pool.removeAt(0).reInit(section, helper)
            }
        }
    }

    private fun reInit(section: SectionState, helper: LayoutHelper): SectionChild {
        this.section = section
        this.helper = helper
        return this
    }

    override fun done() {
        pool.add(this)
    }

    override val numViews get() = section.numViews

    override val isRemoved: Boolean
        get() = false

    private var _measuredWidth: Int = 0
    override val measuredWidth: Int
        get() = _measuredWidth

    override val measuredHeight: Int
        get() = Child.INVALID

    override fun measure(usedWidth: Int, usedHeight: Int) {
        _measuredWidth = helper.layoutWidth - usedWidth
    }

    protected var _left = 0
    override val left: Int
        get() = _left

    protected var _top = 0
    override val top: Int
        get() = _top

    protected var _right = 0
    override val right: Int
        get() = _right

    override val bottom: Int
        get() = Child.INVALID

    override fun layout(left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int) {
        _left = left
        _top = top
        _right = right
        section.layout(helper, left, top, right, numViewsBefore)
    }

    override fun fillTop(dy: Int, left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int): Int {
        return section.fillTop(dy, left, top, right, helper, numViewsBefore)
    }

    override fun trimTop(scrolled: Int, top: Int, helper: LayoutHelper, numViewsBefore: Int): Int {
        return section.trimTop(scrolled, top, helper, numViewsBefore)
    }

    override fun fillBottom(dy: Int, left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int): Int {
        return section.fillBottom(dy, left, top, right, helper, numViewsBefore)
    }

    override fun trimBottom(scrolled: Int, top: Int, helper: LayoutHelper, numViewsBefore: Int): Int {
        return section.trimBottom(scrolled, top, helper, numViewsBefore)
    }

    override val width: Int
        get() = _right - _left
    override val height: Int
        get() = section.height

    override fun addToRecyclerView(i: Int) {
    }
}

private open class ItemChild(var view: View, helper: LayoutHelper, var positionInAdapter: Int) : ChildInternal(helper) {
    companion object {
        val pool = arrayListOf<ItemChild>()

        fun wrap(pos: Int, helper: LayoutHelper): ItemChild {
            val view = helper.getView(pos)
            return if (pool.isEmpty()) {
                ItemChild(view, helper, pos)
            } else {
                pool.removeAt(0).reInit(view, helper, pos)
            }
        }
    }

    private fun reInit(view: View, helper: LayoutHelper, positionInAdapter: Int): ItemChild {
        this.view = view
        this.helper = helper
        this.positionInAdapter = positionInAdapter
        return this
    }

    override fun done() {
        pool.add(this)
    }

    override val isRemoved: Boolean
        get() = view.rvLayoutParams.isItemRemoved

    override val measuredWidth: Int
        get() = helper.getMeasuredWidth(view)
    override val measuredHeight: Int
        get() = helper.getMeasuredHeight(view)

    override fun measure(usedWidth: Int, usedHeight: Int) {
        helper.measure(view, usedWidth, usedHeight)
    }

    override val left: Int
        get() = helper.getLeft(view)
    override val top: Int
        get() = helper.getTop(view)
    override val right: Int
        get() = helper.getRight(view)
    override val bottom: Int
        get() = helper.getBottom(view)

    override fun layout(left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int) {
        val m = view.rvLayoutParams
        helper.layout(view, left, top, right, bottom, m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin)
    }

    override fun fillTop(dy: Int, left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int): Int {
        layout(left, top, right, bottom, numViewsBefore)
        return height
    }

    override fun fillBottom(dy: Int, left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int): Int {
        layout(left, top, right, bottom, numViewsBefore)
        return height
    }

    override val width: Int
        get() = helper.getMeasuredWidth(view)
    override val height: Int
        get() = helper.getMeasuredHeight(view)

    override fun addToRecyclerView(i: Int) {
        helper.addView(view, i)
    }
}

private class DisappearingItemChild(view: View, helper: LayoutHelper, positionInAdapter: Int) :
        ItemChild(view, helper, positionInAdapter) {
    companion object {
        val pool = arrayListOf<DisappearingItemChild>()

        fun wrap(pos: Int, helper: LayoutHelper): DisappearingItemChild {
            val view = helper.getView(pos)
            return if (pool.isEmpty()) {
                DisappearingItemChild(view, helper, pos)
            } else {
                pool.removeAt(0).reInit(view, helper)
            }
        }
    }

    private fun reInit(view: View, helper: LayoutHelper): DisappearingItemChild {
        this.view = view
        this.helper = helper
        return this
    }

    override fun done() {
        pool.add(this)
    }

    override fun addToRecyclerView(i: Int) {
        helper.addDisappearingView(view, i)
    }
}

/**
 * A child which fakes measurement and layout, and never adds to the recycler view.
 */
private class DummyChild(helper: LayoutHelper) : ChildInternal(helper) {
    companion object {
        val pool = arrayListOf<DummyChild>()

        fun wrap(helper: LayoutHelper): DummyChild {
            return if (pool.isEmpty()) {
                DummyChild(helper)
            } else {
                pool.removeAt(0).reInit(helper)
            }
        }
    }

    private fun reInit(helper: LayoutHelper): DummyChild {
        this.helper = helper
        return this
    }

    override fun done() {
        pool.add(this)
    }

    override val isRemoved: Boolean get() = false
    private var _measuredWidth = 0
    override val measuredWidth: Int get() = _measuredWidth
    override val measuredHeight: Int get() = 0

    override fun measure(usedWidth: Int, usedHeight: Int) {
        _width = helper.layoutWidth - usedWidth
    }

    private var _left = 0
    private var _top = 0
    private var _right = 0
    private var _bottom = 0

    override val left: Int get() = _left
    override val top: Int get() = _top
    override val right: Int get() = _right
    override val bottom: Int get() = _bottom

    override fun layout(left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int) {
        _left = left
        _top = top
        _right = right
        _bottom = bottom

        _width = right - left
        _height = bottom - top
    }

    override fun fillTop(dy: Int, left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int): Int {
        layout(left, top, right, bottom, numViewsBefore)
        return height
    }

    override fun fillBottom(dy: Int, left: Int, top: Int, right: Int, bottom: Int, numViewsBefore: Int): Int {
        layout(left, top, right, bottom, numViewsBefore)
        return height
    }

    private var _width = 0
    private var _height = 0

    override val width: Int get() = _width
    override val height: Int get() = _height

    override fun addToRecyclerView(i: Int) {
    }
}
