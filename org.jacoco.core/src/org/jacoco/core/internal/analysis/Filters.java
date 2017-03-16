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

final class Filters {

	interface IOutput {

		void ignore(AbstractInsnNode instruction);

	}

	static void filter(final MethodNode methodNode, final IOutput output) {
		AbstractInsnNode i = methodNode.instructions.getFirst();
		while (i != null) {
			filterTryWithResources(i, output);
			i = i.getNext();
		}
	}

	/**
	 * Filters code that is generated for try-with-resources (Java 7).
	 */
	private static void filterTryWithResources(final AbstractInsnNode from,
			final IOutput output) {
		final AbstractInsnNode to = TryWithResourcesMatcher.match(from);
		if (to == null) {
			return;
		}
		for (AbstractInsnNode i = from; i != to; i = i.getNext()) {
			output.ignore(i);
		}
		// important when return is the last statement of body:
		output.ignore(to);
	}

}
