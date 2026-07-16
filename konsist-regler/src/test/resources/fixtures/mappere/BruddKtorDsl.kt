package fixtures.mappere

fun konfigurerContentNegotiation() {
    install(ContentNegotiation) {
        jackson3 {
        }
    }
}
