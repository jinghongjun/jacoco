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

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

final class Filters {

	interface IOutput {

		void ignore(AbstractInsnNode instruction);

	}

	/**
	 * Filters code that is generated for synchronized statement.
	 */
	static void filter(final MethodNode methodNode, final IOutput output) {
		for (TryCatchBlockNode tryCatch : methodNode.tryCatchBlocks) {
			final AbstractInsnNode to = SynchronizedMatcher.match(tryCatch);
			if (to == null) {
				continue;
			}
			for (AbstractInsnNode i = tryCatch.handler; i != to; i = i
					.getNext()) {
				output.ignore(i);
			}
			output.ignore(to);
		}
	}

}
