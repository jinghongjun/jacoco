/*******************************************************************************
 * Copyright (c) 2009, 2017 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis;

import org.jacoco.core.internal.Java9Support;
import org.jacoco.core.internal.instr.InstrSupport;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TryWithResourcesMatcherTest {

	private final MethodNode m = new MethodNode(InstrSupport.ASM_API_VERSION, 0,
			"name", "()V", null, null);

	private static void asmify(String filename) throws IOException {
		final byte[] bytes = Java9Support.downgrade(
				Java9Support.readFully(new FileInputStream(filename)));
		final ClassReader cr = new ClassReader(bytes);
		cr.accept(
				new TraceClassVisitor(null, new ASMifier(),
						new PrintWriter(System.out)),
				ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
	}

	@Test
	public void javac() {
		// primaryExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 1);

		// r.open
		m.visitInsn(Opcodes.NOP);

		Label end = new Label();
		// "finally" on a normal path
		{
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitJumpInsn(Opcodes.IFNULL, end);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			Label l12 = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, l12);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
			m.visitJumpInsn(Opcodes.GOTO, end);

			// catch (Throwable suppressedExc)
			m.visitVarInsn(Opcodes.ASTORE, 3);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitJumpInsn(Opcodes.GOTO, end);

			m.visitLabel(l12);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
			m.visitJumpInsn(Opcodes.GOTO, end);
		}
		// catch (Throwable t)
		{
			m.visitVarInsn(Opcodes.ASTORE, 3);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitInsn(Opcodes.ATHROW);
		}
		// catch (any)
		m.visitVarInsn(Opcodes.ASTORE, 4);
		// "finally" on exceptional path
		{
			m.visitVarInsn(Opcodes.ALOAD, 1);
			Label l13 = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, l13);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			Label l14 = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, l14);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
			m.visitJumpInsn(Opcodes.GOTO, l13);

			m.visitVarInsn(Opcodes.ASTORE, 5);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitJumpInsn(Opcodes.GOTO, l13);
			m.visitLabel(l14);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
			m.visitLabel(l13);
		}
		m.visitVarInsn(Opcodes.ALOAD, 4);
		m.visitInsn(Opcodes.ATHROW);

		m.visitLabel(end);

		assertMatch(3, 43);
	}

	@Test
	public void javac9_omitted_null_check() {
		// primaryExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 1);

		// r.open
		m.visitInsn(Opcodes.NOP);

		Label end = new Label();
		// "finally" on a normal path
		{
			// if (primaryExc != null)
			m.visitVarInsn(Opcodes.ALOAD, 2);
			Label closeLabel = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, closeLabel);
			// r.close
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
			m.visitJumpInsn(Opcodes.GOTO, end);

			// catch (Throwable suppressedExc)
			m.visitVarInsn(Opcodes.ASTORE, 3);
			// primaryExc.addSuppressed(suppressedExc)
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitJumpInsn(Opcodes.GOTO, end);

			m.visitLabel(closeLabel);
			// r.close()
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
		}
		m.visitJumpInsn(Opcodes.GOTO, end);
		// catch (Throwable t)
		{
			m.visitVarInsn(Opcodes.ASTORE, 3);
			// primaryExc = t
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitVarInsn(Opcodes.ASTORE, 2);
			// throw t
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitInsn(Opcodes.ATHROW);
		}
		// catch (any)
		m.visitVarInsn(Opcodes.ASTORE, 4);
		// "finally" on exceptional path
		{
			// if (primaryExc != null)
			m.visitVarInsn(Opcodes.ALOAD, 2);
			Label closeLabel = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, closeLabel);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
			Label finallyEndLabel = new Label();
			m.visitJumpInsn(Opcodes.GOTO, finallyEndLabel);

			// catch (Throwable suppressedExc)
			m.visitVarInsn(Opcodes.ASTORE, 5);
			// primaryExc.addSuppressed(suppressedExc)
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitJumpInsn(Opcodes.GOTO, finallyEndLabel);

			m.visitLabel(closeLabel);
			// r.close()
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Resource", "close", "()V",
					false);
			m.visitLabel(finallyEndLabel);
		}
		m.visitVarInsn(Opcodes.ALOAD, 4);
		m.visitInsn(Opcodes.ATHROW);

		m.visitLabel(end);

		assertMatch(3, 39);
	}

	@Test
	public void javac9_method() {
		// primaryExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 1);

		// r.open
		m.visitInsn(Opcodes.NOP);

		final Label end = new Label();
		// "finally" on a normal path
		{
			// if (r != null)
			m.visitVarInsn(Opcodes.ALOAD, 0);
			m.visitJumpInsn(Opcodes.IFNULL, end);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 0);
			m.visitMethodInsn(Opcodes.INVOKESTATIC, "CurrentClass",
					"$closeResource",
					"(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V", false);
			m.visitJumpInsn(Opcodes.GOTO, end);
		}

		// catch (Throwable t)
		{
			m.visitVarInsn(Opcodes.ASTORE, 2);
			// primaryExc = t
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ASTORE, 1);
			// throw t
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitInsn(Opcodes.ATHROW);
		}
		// catch (any)
		m.visitVarInsn(Opcodes.ASTORE, 3);
		// "finally" on exceptional path
		{
			// if (r != null)
			m.visitVarInsn(Opcodes.ALOAD, 0);
			final Label finallyEndLabel = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, finallyEndLabel);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 0);
			m.visitMethodInsn(Opcodes.INVOKESTATIC, "CurrentClass",
					"$closeResource",
					"(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V", false);
			m.visitLabel(finallyEndLabel);
		}
		m.visitVarInsn(Opcodes.ALOAD, 3);
		m.visitInsn(Opcodes.ATHROW);

		m.visitLabel(end);

		assertMatch(3, 23);
	}

	@Test
	public void javac9_omitted_null_check_and_method() {
		// primaryExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 1);

		// r.open
		m.visitInsn(Opcodes.NOP);

		// "finally" on a normal path
		{
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKESTATIC, "CurrentClass",
					"$closeResource",
					"(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V", false);
		}
		Label end = new Label();
		m.visitJumpInsn(Opcodes.GOTO, end);
		// catch (Throwable t)
		{
			m.visitVarInsn(Opcodes.ASTORE, 3);
			// primaryExc = t
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitVarInsn(Opcodes.ASTORE, 2);
			// throw t
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitInsn(Opcodes.ATHROW);
		}
		// catch (any)
		m.visitVarInsn(Opcodes.ASTORE, 4);
		// "finally" on exceptional path
		{
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitMethodInsn(Opcodes.INVOKESTATIC, "CurrentClass",
					"$closeResource",
					"(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V", false);
		}
		m.visitVarInsn(Opcodes.ALOAD, 4);
		m.visitInsn(Opcodes.ATHROW);

		m.visitLabel(end);

		assertMatch(3, 18);
	}

	@Test
	public void ecj() throws IOException {
		// primaryExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 1);
		// suppressedExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 2);

		final Label l4 = new Label();
		final Label l7 = new Label();
		final Label end = new Label();
		{ // nextIsEcjClose("r0")
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitJumpInsn(Opcodes.IFNULL, l4);
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun2$Resource", "close",
					"()V", false);
		}
		m.visitJumpInsn(Opcodes.GOTO, l4);
		{ // nextIsEcjCloseAndThrow("r0")
			m.visitVarInsn(Opcodes.ASTORE, 1);
			m.visitVarInsn(Opcodes.ALOAD, 5);
			Label l11 = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, l11);
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun2$Resource", "close",
					"()V", false);
			m.visitLabel(l11);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitInsn(Opcodes.ATHROW);
		}
		m.visitLabel(l4);
		{ // nextIsEcjClose("r1")
			m.visitVarInsn(Opcodes.ALOAD, 4);
			m.visitJumpInsn(Opcodes.IFNULL, l7);
			m.visitVarInsn(Opcodes.ALOAD, 4);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun2$Resource", "close",
					"()V", false);
		}
		m.visitJumpInsn(Opcodes.GOTO, l7);
		{ // nextIsEcjSuppress
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			final Label suppressStart = new Label();
			m.visitJumpInsn(Opcodes.IFNONNULL, suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ASTORE, 1);
			final Label suppressEnd = new Label();
			m.visitJumpInsn(Opcodes.GOTO, suppressEnd);
			m.visitLabel(suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitJumpInsn(Opcodes.IF_ACMPEQ, suppressEnd);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitLabel(suppressEnd);
		}
		{ // nextIsEcjCloseAndThrow("r1")
			m.visitVarInsn(Opcodes.ALOAD, 4);
			final Label l14 = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, l14);
			m.visitVarInsn(Opcodes.ALOAD, 4);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun2$Resource", "close",
					"()V", false);
			m.visitLabel(l14);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitInsn(Opcodes.ATHROW);
		}
		m.visitLabel(l7);
		{ // nextIsEcjClose("r2")
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitJumpInsn(Opcodes.IFNULL, end);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun2$Resource", "close",
					"()V", false);
			m.visitJumpInsn(Opcodes.GOTO, end);
		}
		{ // nextIsEcjSuppress
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			final Label suppressStart = new Label();
			m.visitJumpInsn(Opcodes.IFNONNULL, suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ASTORE, 1);
			final Label suppressEnd = new Label();
			m.visitJumpInsn(Opcodes.GOTO, suppressEnd);
			m.visitLabel(suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitJumpInsn(Opcodes.IF_ACMPEQ, suppressEnd);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitLabel(suppressEnd);
		}
		{ // nextIsEcjCloseAndThrow("r2")
			m.visitVarInsn(Opcodes.ALOAD, 3);
			final Label l18 = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, l18);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun2$Resource", "close",
					"()V", false);
			m.visitLabel(l18);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitInsn(Opcodes.ATHROW);
		}
		{ // nextIsEcjSuppress
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			final Label suppressStart = new Label();
			m.visitJumpInsn(Opcodes.IFNONNULL, suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ASTORE, 1);
			final Label suppressEnd = new Label();
			m.visitJumpInsn(Opcodes.GOTO, suppressEnd);
			m.visitLabel(suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitJumpInsn(Opcodes.IF_ACMPEQ, suppressEnd);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitLabel(suppressEnd);
		}
		// throw primaryExc
		m.visitVarInsn(Opcodes.ALOAD, 1);
		m.visitInsn(Opcodes.ATHROW);

		// additional handlers
		m.visitInsn(Opcodes.NOP);

		m.visitLabel(end);

		assertMatch(4, 86);
	}

	@Test
	public void ecj_noFlowOut() {
		// primaryExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 1);
		// suppressedExc = null
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 2);

		{ // nextIsEcjClose("r0")
			final Label label = new Label();
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitJumpInsn(Opcodes.IFNULL, label);
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun$Resource", "close",
					"()V", false);
			m.visitLabel(label);
		}
		{ // nextIsEcjClose("r1")
			final Label label = new Label();
			m.visitVarInsn(Opcodes.ALOAD, 4);
			m.visitJumpInsn(Opcodes.IFNULL, label);
			m.visitVarInsn(Opcodes.ALOAD, 4);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun$Resource", "close",
					"()V", false);
			m.visitLabel(label);
		}
		{ // nextIsEcjClose("r2")
			final Label label = new Label();
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitJumpInsn(Opcodes.IFNULL, label);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun$Resource", "close",
					"()V", false);
			m.visitLabel(label);
		}

		m.visitInsn(Opcodes.ARETURN);

		// FIXME finally block
		m.visitVarInsn(Opcodes.ASTORE, 1);

		{ // nextIsEcjCloseAndThrow("r0")
			m.visitVarInsn(Opcodes.ALOAD, 5);
			final Label throwLabel = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, throwLabel);
			m.visitVarInsn(Opcodes.ALOAD, 5);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun$Resource", "close",
					"()V", false);
			m.visitLabel(throwLabel);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitInsn(Opcodes.ATHROW);
		}
		{ // nextIsEcjSuppress
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			final Label suppressStart = new Label();
			m.visitJumpInsn(Opcodes.IFNONNULL, suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ASTORE, 1);
			final Label suppressEnd = new Label();
			m.visitJumpInsn(Opcodes.GOTO, suppressEnd);
			m.visitLabel(suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitJumpInsn(Opcodes.IF_ACMPEQ, suppressEnd);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitLabel(suppressEnd);
		}
		{ // nextIsEcjCloseAndThrow("r1")
			m.visitVarInsn(Opcodes.ALOAD, 4);
			final Label throwLabel = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, throwLabel);
			m.visitVarInsn(Opcodes.ALOAD, 4);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun$Resource", "close",
					"()V", false);
			m.visitLabel(throwLabel);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitInsn(Opcodes.ATHROW);
		}
		{ // nextIsEcjSuppress
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			final Label suppressStart = new Label();
			m.visitJumpInsn(Opcodes.IFNONNULL, suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ASTORE, 1);
			final Label suppressEnd = new Label();
			m.visitJumpInsn(Opcodes.GOTO, suppressEnd);
			m.visitLabel(suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitJumpInsn(Opcodes.IF_ACMPEQ, suppressEnd);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitLabel(suppressEnd);
		}
		{ // nextIsEcjCloseAndThrow("r2")
			m.visitVarInsn(Opcodes.ALOAD, 3);
			final Label throwLabel = new Label();
			m.visitJumpInsn(Opcodes.IFNULL, throwLabel);
			m.visitVarInsn(Opcodes.ALOAD, 3);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "Fun$Resource", "close",
					"()V", false);
			m.visitLabel(throwLabel);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitInsn(Opcodes.ATHROW);
		}
		{ // nextIsEcjSuppress
			m.visitVarInsn(Opcodes.ASTORE, 2);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			final Label suppressStart = new Label();
			m.visitJumpInsn(Opcodes.IFNONNULL, suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitVarInsn(Opcodes.ASTORE, 1);
			final Label suppressEnd = new Label();
			m.visitJumpInsn(Opcodes.GOTO, suppressEnd);
			m.visitLabel(suppressStart);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitJumpInsn(Opcodes.IF_ACMPEQ, suppressEnd);
			m.visitVarInsn(Opcodes.ALOAD, 1);
			m.visitVarInsn(Opcodes.ALOAD, 2);
			m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable",
					"addSuppressed", "(Ljava/lang/Throwable;)V", false);
			m.visitLabel(suppressEnd);
		}
		// throw primaryExc
		m.visitVarInsn(Opcodes.ALOAD, 1);
		m.visitInsn(Opcodes.ATHROW);

		// additional handlers
		m.visitInsn(Opcodes.NOP);

		assertMatch(4, 85);
	}

	private void assertMatch(final int start, final int end) {
		print();

		final AbstractInsnNode endNode = TryWithResourcesMatcher
				.match(m.instructions.get(start));
		assertNotNull(endNode);
		for (int i = 0; i < m.instructions.size(); i++) {
			if (endNode == m.instructions.get(i)) {
				assertEquals(end, i);
			}
		}
	}

	private void print() {
		final PrintWriter pw = new PrintWriter(System.out);
		final TraceMethodVisitor mv = new TraceMethodVisitor(new Textifier());
		for (int i = 0; i < m.instructions.size(); i++) {
			m.instructions.get(i).accept(mv);
			pw.format("%3d:", i);
			mv.p.print(pw);
			mv.p.getText().clear();
		}
		pw.flush();
	}

}
