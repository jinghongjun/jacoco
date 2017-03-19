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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

final class SynchronizedMatcher {

	private final AbstractInsnNode start;
	private AbstractInsnNode cursor;

	private SynchronizedMatcher(final AbstractInsnNode start) {
		this.start = start;
	}

	public static AbstractInsnNode match(final TryCatchBlockNode tryCatch) {
		if (tryCatch.type != null) {
			return null;
		}
		if (tryCatch.start == tryCatch.handler) {
			return null;
		}
		return new SynchronizedMatcher(tryCatch.handler).match();
	}

	private AbstractInsnNode match() {
		if (nextIsEcj() || nextIsJavac()) {
			return cursor;
		}
		return null;
	}

	private boolean nextIsJavac() {
		cursor = start;
		return nextIs(Opcodes.ASTORE) && nextIs(Opcodes.ALOAD)
				&& nextIs(Opcodes.MONITOREXIT) && nextIs(Opcodes.ALOAD)
				&& nextIs(Opcodes.ATHROW);
	}

	private boolean nextIsEcj() {
		cursor = start;
		return nextIs(Opcodes.ALOAD) && nextIs(Opcodes.MONITOREXIT)
				&& nextIs(Opcodes.ATHROW);
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
