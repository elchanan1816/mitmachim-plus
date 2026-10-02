package community.mitmachim.nativeapp

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class MentionSuggestionsTest {
 @Test fun detectsHebrewAtCursorNotEmail(){assertEquals("רב",mentionQuery(TextFieldValue("שלום @רב",TextRange(8)))?.query);assertNull(mentionQuery(TextFieldValue("mail@host",TextRange(9))));assertNull(mentionQuery(TextFieldValue("@שם אחר",TextRange(7))))}
 @Test fun replacesOnlyActiveMentionAndPreservesSuffix(){val input=TextFieldValue("היי @רב המשך",TextRange(7));val query=mentionQuery(input)!!;val result=insertMention(input,query,"רב-יהודה-פרחים");assertEquals("היי @רב-יהודה-פרחים  המשך",result.text);assertEquals("היי @רב-יהודה-פרחים ".length,result.selection.end)}
 @Test fun rejectsStaleQueryAndInvalidSlug(){val input=TextFieldValue("@ab",TextRange(3));val q=mentionQuery(input)!!;assertEquals(input,insertMention(input,q,"שם עם רווח"));assertEquals(input,insertMention(input,q.copy(query="old"),"valid"))}
}
