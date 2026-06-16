package com.pda.app

import com.pda.app.data.api.model.UserInfoDto
import com.pda.app.data.api.model.WarehouseDto
import com.pda.app.data.session.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagerTest {

    private val user = UserInfoDto(
        userId = "u1", username = "admin", email = "a@b.com", fullName = "张伟"
    )
    private val warehouse = WarehouseDto(1, "WH01", "深圳总仓", true)

    @Test
    fun `start sets token and user`() {
        val sm = SessionManager()
        sm.start("tok123", user)
        val session = sm.session.value
        assertEquals("tok123", session?.token)
        assertEquals("张伟", session?.user?.fullName)
        assertEquals("tok123", sm.currentToken)
    }

    @Test
    fun `selectWarehouse updates selected warehouse`() {
        val sm = SessionManager()
        sm.start("tok123", user)
        sm.selectWarehouse(warehouse)
        assertEquals(warehouse, sm.session.value?.selectedWarehouse)
    }

    @Test
    fun `clear resets session to null`() {
        val sm = SessionManager()
        sm.start("tok123", user)
        sm.clear()
        assertNull(sm.session.value)
        assertNull(sm.currentToken)
    }

    @Test
    fun `selectWarehouse without session is ignored`() {
        val sm = SessionManager()
        sm.selectWarehouse(warehouse)
        assertNull(sm.session.value)
    }
}
