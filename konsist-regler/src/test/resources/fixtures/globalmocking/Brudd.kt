package fixtures.globalmocking

import io.mockk.clearAllMocks
import io.mockk.mockkStatic
import org.junit.jupiter.api.Test

internal class Brudd {

    @Test
    fun `mocker globalt`() {
        mockkStatic(Runtime::class)
        io.mockk.mockkObject(EtObjekt)
        clearAllMocks()
    }
}
