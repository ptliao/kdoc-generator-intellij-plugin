package siosio.kodkod

import com.intellij.codeInsight.*
import com.intellij.codeInsight.editorActions.enter.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.editor.*
import com.intellij.psi.*
import com.intellij.psi.codeStyle.*
import com.intellij.psi.util.*
import com.intellij.util.text.*
import org.jetbrains.kotlin.idea.kdoc.*
import org.jetbrains.kotlin.kdoc.psi.api.*
import org.jetbrains.kotlin.kdoc.psi.impl.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

/**
 *
 */
class EnterAfterKDocGenHandler : EnterHandlerDelegateAdapter() {


    override fun postProcessEnter(file: PsiFile,
                                  editor: Editor,
                                  dataContext: DataContext): EnterHandlerDelegate.Result {

        if (file !is KtFile || !CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
            return EnterHandlerDelegate.Result.Continue
        }

        val caretModel = editor.caretModel
        if (!isInKDoc(editor, caretModel.offset)) {
            return EnterHandlerDelegate.Result.Continue
        }

        val project = file.project
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(file) ?: return EnterHandlerDelegate.Result.Continue
        documentManager.commitDocument(document)

        ApplicationManager.getApplication().runWriteAction {
            val elementAtCaret = file.findElementAt(caretModel.offset)
            val kdoc = PsiTreeUtil.getParentOfType(elementAtCaret, KDoc::class.java) ?: return@runWriteAction
            val kDocElementFactory = KDocElementFactory(project)

            val parent = kdoc.parent
            when (parent) {
                is KtNamedFunction -> {
                    val params: List<PsiNameIdentifierOwner> = parent.typeParameters + parent.valueParameters

                    params.map { it.name }
                            .map { "@param $it" }
                            .joinToString("\n", transform = { "* $it" })
                            .let { "/**\n* TODO\n$it\n*/" }
                            .let { kDocElementFactory.createKDocFromText(it) }
                            .let { kdoc.replace(it) }
                            .let { CodeStyleManager.getInstance(project).reformat(it) }
                }
                else -> return@runWriteAction
            }.let {
                it.getChildOfType<KDocSection>()?.let {
                    caretModel.moveToOffset(it.textOffset + 6)
                }
            }

        }
        return EnterHandlerDelegate.Result.Continue
    }

    private fun isInKDoc(editor: Editor, offset: Int): Boolean {
        val document = editor.document
        val docChars = document.charsSequence
        var i = CharArrayUtil.lastIndexOf(docChars, "/**", offset)
        if (i >= 0) {
            i = CharArrayUtil.indexOf(docChars, "*/", i)
            return i > offset
        }
        return false
    }
}