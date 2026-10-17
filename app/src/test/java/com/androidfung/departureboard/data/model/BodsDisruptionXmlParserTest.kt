package com.androidfung.departureboard.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BodsDisruptionXmlParserTest {

    @Test
    fun parse_extractsDisruptionSituations() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
                <ServiceDelivery>
                    <SituationExchangeDelivery>
                        <PtSituationElement>
                            <SituationNumber>GB:BODS:SITUATION:12345</SituationNumber>
                            <LineRef>310</LineRef>
                            <Summary>Roadworks at Broxbourne High Street</Summary>
                            <Description>Delays expected of up to 15 minutes due to temporary traffic lights.</Description>
                            <Reason>roadworks</Reason>
                            <Severity>slight</Severity>
                        </PtSituationElement>
                    </SituationExchangeDelivery>
                </ServiceDelivery>
            </Siri>
        """.trimIndent()

        val disruptions = BodsDisruptionXmlParser.parse(xml)
        assertEquals(1, disruptions.size)
        val d = disruptions[0]
        assertEquals("GB:BODS:SITUATION:12345", d.situationNumber)
        assertEquals("310", d.lineRef)
        assertEquals("Roadworks at Broxbourne High Street", d.summary)
        assertEquals("roadworks", d.reason)
        assertEquals("slight", d.severity)
    }
}
