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

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class TryWithResourcesMatcher {

	private final AbstractInsnNode start;

	private AbstractInsnNode cursor;

	/**
	 * @return end of a sequence of instructions that closes resources in
	 *         try-with-resources, if given node points on a start of such
	 *         sequence, <code>null</code> otherwise
	 */
	public static AbstractInsnNode match(final AbstractInsnNode start) {
		return new TryWithResourcesMatcher(start).match();
	}

	private TryWithResourcesMatcher(final AbstractInsnNode start) {
		this.start = start;
	}

	private enum Pattern {
		ECJ, JAVAC_OPTIMAL, JAVAC_FULL, JAVAC_OMITTED_NULL_CHECK, JAVAC_METHOD,
	}

	public AbstractInsnNode match() {
		if (start.getPrevious() == null) {
			return null;
		}
		for (Pattern t : Pattern.values()) {
			cursor = start.getPrevious();
			vars.clear();
			labels.clear();
			owner.clear();
			if (matches(t)) {
				return cursor;
			}
		}
		return null;
	}

	private static final String PRIMARY_EXC = "primaryExc";
	private static final String SUPPRESSED_EXC = "suppressedExc";

	private boolean matches(Pattern pattern) {
		switch (pattern) {
		case ECJ:
			return matchEcj();
		case JAVAC_OPTIMAL:
			return nextIsCloseResource()
					// goto or return
					&& nextIsReturn()
					// "catch (Throwable t)"
					&& nextIsSavePrimaryExc() && nextIs(Opcodes.ASTORE)
					&& nextIsCloseResource() && nextIs(Opcodes.ALOAD)
					&& nextIs(Opcodes.ATHROW)
					// label
					&& nextIsReturnLabel();
		default:
			return nextIsFinally(pattern, "normal")
					// goto or return
					&& nextIsReturn()
					// "catch (Throwable t)"
					&& nextIsSavePrimaryExc() && nextIsVar(Opcodes.ASTORE, "t2")
					&& nextIsFinally(pattern, "exceptional")
					&& nextIsLabel("exceptional.finallyExit")
					&& nextIsVar(Opcodes.ALOAD, "t2") && nextIs(Opcodes.ATHROW)
					// label
					&& nextIsReturnLabel();
		}
	}

	private boolean nextIsReturn() {
		final AbstractInsnNode c = cursor;
		next();
		if (Opcodes.ILOAD <= cursor.getOpcode()
				&& cursor.getOpcode() <= Opcodes.ALOAD) {
			labels.remove("normal.finallyExit");
			next();
			final int opcode = cursor.getOpcode();
			return Opcodes.IRETURN <= opcode && opcode <= Opcodes.ARETURN;
		}
		cursor = c;
		return nextIsJump(Opcodes.GOTO, "normal.finallyExit");
	}

	private boolean nextIsReturnLabel() {
		return !labels.containsKey("normal.finallyExit")
				|| nextIsLabel("normal.finallyExit");
	}

	private boolean nextIsFinally(Pattern pattern, String ctx) {
		switch (pattern) {
		case JAVAC_FULL:
			return nextIs(Opcodes.ALOAD)
					// "if (r != null)"
					&& nextIsJump(Opcodes.IFNULL, ctx + ".finallyExit")
					// "if (primaryExc != null)"
					&& nextIsVar(Opcodes.ALOAD, PRIMARY_EXC)
					&& nextIsJump(Opcodes.IFNULL, ctx + ".closeLabel")
					// "r.close()"
					&& nextIsClose("r")
					&& nextIsJump(Opcodes.GOTO, ctx + ".finallyExit")
					// "catch (Throwable t)"
					&& nextIsVar(Opcodes.ASTORE, ctx + ".t")
					// "primaryExc.addSuppressed(suppressedExc)"
					&& nextIsAddSuppressed(ctx + ".t")
					&& nextIsJump(Opcodes.GOTO, ctx + ".finallyExit")
					// "r.close()"
					&& nextIsLabel(ctx + "closeLabel") && nextIsClose("r");
		case JAVAC_OMITTED_NULL_CHECK:
			return nextIsVar(Opcodes.ALOAD, PRIMARY_EXC)
					// "if (primaryExc != null)"
					&& nextIsJump(Opcodes.IFNULL, ctx + ".closeLabel")
					// "r.close()"
					&& nextIsClose("r")
					&& nextIsJump(Opcodes.GOTO, ctx + ".finallyExit")
					// "catch (Throwable t)"
					&& nextIsVar(Opcodes.ASTORE, ctx + ".t")
					// "primaryExc.addSuppressed(suppressedExc)"
					&& nextIsAddSuppressed(ctx + ".t")
					&& nextIsJump(Opcodes.GOTO, ctx + ".finallyExit")
					// "r.close()"
					&& nextIsLabel(ctx + "closeLabel") && nextIsClose("r");
		case JAVAC_METHOD:
			return nextIs(Opcodes.ALOAD)
					// "if (primaryExc != null)"
					&& nextIsJump(Opcodes.IFNULL, ctx + ".finallyExit")
					&& nextIsCloseResource();
		}
		return false;
	}

	private boolean nextIsCloseResource() {
		if (nextIsVar(Opcodes.ALOAD, PRIMARY_EXC)
				&& nextIsVar(Opcodes.ALOAD, SUPPRESSED_EXC)
				&& nextIs(Opcodes.INVOKESTATIC)) {
			final MethodInsnNode m = (MethodInsnNode) cursor;
			return "$closeResource".equals(m.name)
					&& "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V"
							.equals(m.desc);
		}
		return false;
	}

	private boolean nextIsSavePrimaryExc() {
		return nextIs(Opcodes.ASTORE)
				// "primaryExc = t"
				&& nextIsVar(Opcodes.ALOAD, "t1")
				&& nextIsVar(Opcodes.ASTORE, PRIMARY_EXC)
				// "throw t"
				&& nextIsVar(Opcodes.ALOAD, "t1") && nextIs(Opcodes.ATHROW);
	}

	private boolean matchEcj() {
		if (!nextIsEcjClose("r0")) {
			return false;
		}
		final AbstractInsnNode c = cursor;
		next();
		if (cursor.getOpcode() != Opcodes.GOTO) {
			cursor = c;
			return nextIsEcjNoFlowOut();
		}
		cursor = c;

		if (!nextIsJump(Opcodes.GOTO, "r0.end")) {
			return false;
		}
		// "catch (Throwable t)"
		// "primaryExc = t"
		if (!nextIsVar(Opcodes.ASTORE, PRIMARY_EXC)) {
			return false;
		}
		if (!nextIsEcjCloseAndThrow("r0")) {
			return false;
		}
		// "catch (Throwable t)"
		int i = 0;
		AbstractInsnNode n = cursor;
		while (!nextIsEcjSuppress("last")) {
			cursor = n;
			i++;
			final String r = "r" + i;
			if (!nextIsLabel("r" + (i - 1) + ".end")) {
				return false;
			}
			if (!nextIsEcjClose(r)) {
				return false;
			}
			if (!nextIsJump(Opcodes.GOTO, r + ".end")) {
				return false;
			}
			if (!nextIsEcjSuppress(r)) {
				return false;
			}
			if (!nextIsEcjCloseAndThrow(r)) {
				return false;
			}
			n = cursor;
		}
		// "throw primaryExc"
		return nextIsVar(Opcodes.ALOAD, PRIMARY_EXC) && nextIs(Opcodes.ATHROW)
		// && nextIsLabel("r" + i + ".end")
		;
	}

	private boolean nextIsEcjNoFlowOut() {
		int resources = 1;
		while (true) {
			AbstractInsnNode c = cursor;
			next();
			final int opcode = cursor.getOpcode();
			if (Opcodes.IRETURN <= opcode && opcode <= Opcodes.ARETURN) {
				break;
			}
			cursor = c;
			if (!nextIsEcjClose("r" + resources)) {
				return false;
			}
			resources++;
		}
		// "primaryExc = t"
		if (!nextIsVar(Opcodes.ASTORE, PRIMARY_EXC)) {
			return false;
		}
		for (int r = 0; r < resources; r++) {
			if (!nextIsEcjCloseAndThrow("r" + r)) {
				return false;
			}
			if (!nextIsEcjSuppress("r" + r)) {
				return false;
			}
		}
		// "throw primaryExc"
		return nextIsVar(Opcodes.ALOAD, PRIMARY_EXC) && nextIs(Opcodes.ATHROW);
	}

	private boolean nextIsEcjClose(final String name) {
		return nextIsVar(Opcodes.ALOAD, name)
				// "if (r != null)"
				&& nextIsJump(Opcodes.IFNULL, name + ".end")
				// "r.close()"
				&& nextIsClose(name);
	}

	private boolean nextIsEcjCloseAndThrow(final String name) {
		return nextIsVar(Opcodes.ALOAD, name)
				// "if (r != null)"
				&& nextIsJump(Opcodes.IFNULL, name)
				// "r.close()"
				&& nextIsClose(name) && nextIsLabel(name)
				&& nextIs(Opcodes.ALOAD) && nextIs(Opcodes.ATHROW);
	}

	private boolean nextIsEcjSuppress(String name) {
		return nextIsVar(Opcodes.ASTORE, name + ".t")
				// "suppressedExc = t"
				// "if (primaryExc != null)"
				&& nextIsVar(Opcodes.ALOAD, PRIMARY_EXC)
				&& nextIsJump(Opcodes.IFNONNULL, name + ".suppressStart")
				// "primaryExc = suppressedExc"
				&& nextIs(Opcodes.ALOAD)
				&& nextIsVar(Opcodes.ASTORE, PRIMARY_EXC)
				&& nextIsJump(Opcodes.GOTO, name + ".suppressEnd")
				// "if (primaryExc == suppressedExc)"
				&& nextIsLabel(name + ".suppressStart")
				&& nextIsVar(Opcodes.ALOAD, PRIMARY_EXC)
				&& nextIs(Opcodes.ALOAD)
				&& nextIsJump(Opcodes.IF_ACMPEQ, name + ".suppressEnd")
				// "primaryExc.addSuppressed(suppressedExc)"
				&& nextIsAddSuppressed(name + ".t")
				&& nextIsLabel(name + ".suppressEnd");
	}

	private final Map<String, String> owner = new HashMap<String, String>();

	private boolean nextIsClose(String name) {
		if (!nextIsVar(Opcodes.ALOAD, name)) {
			return false;
		}
		next();
		if (cursor.getOpcode() != Opcodes.INVOKEINTERFACE
				&& cursor.getOpcode() != Opcodes.INVOKEVIRTUAL) {
			return false;
		}
		final MethodInsnNode m = (MethodInsnNode) cursor;
		if (!"close".equals(m.name) || !"()V".equals(m.desc)) {
			return false;
		}
		final String actual = m.owner;
		final String expected = owner.put(name, actual);
		return expected == null || actual.equals(expected);
	}

	private boolean nextIsAddSuppressed(String name) {
		if (!nextIsVar(Opcodes.ALOAD, PRIMARY_EXC)) {
			return false;
		}
		if (!nextIsVar(Opcodes.ALOAD, name)) {
			return false;
		}
		if (!nextIs(Opcodes.INVOKEVIRTUAL)) {
			return false;
		}
		final MethodInsnNode m = (MethodInsnNode) cursor;
		return "java/lang/Throwable".equals(m.owner)
				&& "addSuppressed".equals(m.name);
	}

	private final Map<String, Integer> vars = new HashMap<String, Integer>();

	private boolean nextIsVar(int opcode, String name) {
		if (!nextIs(opcode)) {
			return false;
		}
		final int actual = ((VarInsnNode) cursor).var;
		final Integer expected = vars.put(name, actual);
		return expected == null || expected == actual;
	}

	private final Map<String, LabelNode> labels = new HashMap<String, LabelNode>();

	private boolean nextIsJump(int opcode, String name) {
		if (!nextIs(opcode)) {
			return false;
		}
		final LabelNode actual = ((JumpInsnNode) cursor).label;
		final LabelNode expected = labels.put(name, actual);
		return expected == null || actual == expected;
	}

	private boolean nextIsLabel(String name) {
		cursor = cursor.getNext();
		if (cursor.getType() != AbstractInsnNode.LABEL) {
			return false;
		}
		final LabelNode actual = (LabelNode) cursor;
		final LabelNode expected = labels.put(name, actual);
		return expected == null || actual == expected;
	}

	/**
	 * Moves {@link #cursor} to next instruction and returns <code>true</code>
	 * if it has given opcode.
	 */
	private boolean nextIs(int opcode) {
		next();
		return cursor != null && cursor.getOpcode() == opcode;
	}

	/**
	 * Moves {@link #cursor} to next instruction.
	 */
	private void next() {
		do {
			cursor = cursor.getNext();
		} while (cursor != null && (cursor.getType() == AbstractInsnNode.FRAME
				|| cursor.getType() == AbstractInsnNode.LABEL
				|| cursor.getType() == AbstractInsnNode.LINE));
	}

}
