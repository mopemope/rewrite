/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.rewrite.refactor.op

import com.netflix.rewrite.ast.*
import com.netflix.rewrite.refactor.RefactorVisitor
import org.slf4j.LoggerFactory

class ReorderMethodArguments(val byArgumentNames: List<String>,
                             override val ruleName: String = "reorder-method-arguments"): RefactorVisitor<Tr.MethodInvocation>() {

    private var originalParamNames: Array<out String>? = null
    private val logger = LoggerFactory.getLogger(ReorderMethodArguments::class.java)

    fun setOriginalParamNames(vararg names: String) { originalParamNames = names }

    override fun visitMethodInvocation(meth: Tr.MethodInvocation): List<AstTransform<Tr.MethodInvocation>> {
        if(meth.type is Type.Method) {
            val paramNames = originalParamNames?.toList() ?: meth.type.paramNames ?:
                    error("There is no source attachment for method ${meth.type.declaringType.fullyQualifiedName}.${meth.name.simpleName}(..), " +
                            "provide a reference for original parameter names by calling setOriginalParamNames(..)")

            val resolvedParamCount = when(meth.type.resolvedSignature) {
                null -> {
                    logger.warn("Unable to verify the provided parameter size because the method's resolved signature could not be determined. Original call was: " +
                        meth.printTrimmed())
                    meth.args.args.size
                }
                else -> meth.type.resolvedSignature.paramTypes.size
            }

            var i = 0
            val (reordered, formattings) = byArgumentNames.fold(emptyList<Expression>() to emptyList<Formatting>()) { acc, name ->
                val fromPos = paramNames.indexOf(name)
                if(meth.args.args.size > resolvedParamCount && fromPos == resolvedParamCount - 1) {
                    // this is a varargs argument
                    val varargs = meth.args.args.drop(fromPos)
                    val formatting = meth.args.args.subList(i, (i++) + varargs.size).map(Expression::formatting)
                    acc.first + varargs to
                            acc.second + formatting
                } else if(fromPos >= 0 && meth.args.args.size > fromPos) {
                    acc.first + meth.args.args[fromPos] to
                            acc.second + meth.args.args[i++].formatting
                } else acc
            }

            val reorderedFormatted = reordered.mapIndexed { j, arg -> arg.changeFormatting<Expression>(formattings[j]) }
            return transform { copy(args = args.copy(args = reorderedFormatted)) }
        }

        return super.visitMethodInvocation(meth)
    }
}
