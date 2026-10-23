package io.github.masoudd2159.deviceatlas.presentation.interactive

import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import org.junit.Assert.*
import org.junit.Test

class TouchCoverageTest {
    @Test fun boundariesAndInvalidCoordinates() {
        val grid = TouchCoverage().resize(800,1200)
        assertEquals(0,grid.cell(0f,0f)); assertEquals(95,grid.cell(799.9f,1199.9f))
        assertEquals(9,grid.cell(100f,100f))
        for ((x,y) in listOf(-1f to 0f, 0f to -1f,800f to 0f,0f to 1200f,Float.NaN to 0f,Float.POSITIVE_INFINITY to 1f))
            assertNull(grid.cell(x,y))
        assertNull(TouchCoverage().cell(0f,0f))
    }
    @Test fun boundedCoverageAndDistinctPointerIdentity() {
        var grid = TouchCoverage().resize(800,1200)
        for (y in 0..11) for (x in 0..7) grid = grid.update(listOf(Contact(5,x*100f+1,y*100f+1)))
        assertEquals(96,grid.visited.size)
        grid = grid.update((1L..15L).map { Contact(it,1f,1f) })
        assertEquals(15,grid.activeCount); assertEquals(15,grid.maximumContacts); assertEquals(10,grid.contacts.size)
        grid = grid.update(emptyList()); assertEquals(0,grid.activeCount); assertEquals(15,grid.maximumContacts)
        grid = grid.update(listOf(Contact(1,1f,1f),Contact(1,1f,1f))); assertEquals(1,grid.activeCount)
    }
    @Test fun resizeResetsRatherThanMisrepresentingObservedCells() {
        val grid = TouchCoverage().resize(800,1200).update(listOf(Contact(1,20f,20f)))
        assertEquals(grid,grid.resize(800,1200))
        val resized = grid.resize(1200,800)
        assertTrue(resized.geometryReset); assertTrue(resized.visited.isEmpty()); assertTrue(resized.contacts.isEmpty())
        assertEquals(12,resized.columns); assertEquals(8,resized.rows); assertTrue(resized.cells <= 128)
    }
}
