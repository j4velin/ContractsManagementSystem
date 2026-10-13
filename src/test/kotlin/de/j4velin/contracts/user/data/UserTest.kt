package de.j4velin.contracts.user.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

class UserTest {

    private val dto = UserDTO(username = "user", password = "secret", mail = "user@example.com")

    // same mapping metadata the repository uses to decide between insert and update; no MongoDB needed
    private val entity = MongoMappingContext().getRequiredPersistentEntity(User::class.java)

    @Test
    fun `user created from DTO has no id and is treated as new`() {
        val user = User(dto, ENCODED_PASSWORD)
        assertNull(user.id)
        assertTrue(entity.isNew(user))
    }

    @Test
    fun `copy of a persisted user keeps its id and is not treated as new`() {
        val persisted = User(dto, ENCODED_PASSWORD).copy(id = "61234567890abcdef1234567")
        val updated = persisted.copy(locale = "de")
        assertEquals("61234567890abcdef1234567", updated.id)
        assertEquals("61234567890abcdef1234567", updated.requireId())
        assertFalse(entity.isNew(updated))
    }

    @Test
    fun `requireId fails for a user without id`() {
        assertThrows<IllegalStateException> { User(dto, ENCODED_PASSWORD).requireId() }
    }

    @Test
    fun `user created from DTO uses the given encoded password`() {
        val user = User(dto, ENCODED_PASSWORD)
        assertEquals(ENCODED_PASSWORD, user.password)
        assertEquals("user", user.username)
        assertEquals("user@example.com", user.mail)
    }

    @Test
    fun `toString does not contain the password`() {
        val string = User(dto, ENCODED_PASSWORD).copy(id = "u1").toString()
        assertFalse(string.contains(ENCODED_PASSWORD), string)
        assertTrue(string.contains("password=[PROTECTED]"), string)
        assertTrue(string.contains("id=u1"), string)
        assertTrue(string.contains("username=user"), string)
    }

    private companion object {
        const val ENCODED_PASSWORD = "\$2a\$10\$encodedPasswordHash"
    }
}
