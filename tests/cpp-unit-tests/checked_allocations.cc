/* Copyright (C) 2012 Red Hat

 This file is part of IcedTea.

 IcedTea is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2, or (at your option)
 any later version.

 IcedTea is distributed in the hope that it will be useful, but
 WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with IcedTea; see the file COPYING.  If not, write to the
 Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 02110-1301 USA.

 Linking this library statically or dynamically with other modules is
 making a combined work based on this library.  Thus, the terms and
 conditions of the GNU General Public License cover the whole
 combination.

 As a special exception, the copyright holders of this library give you
 permission to link this library with independent modules to produce an
 executable, regardless of the license terms of these independent
 modules, and to copy and distribute the resulting executable under
 terms of your choice, provided that you also meet, for each linked
 independent module, the terms and conditions of the license of that
 module.  An independent module is a module which is not derived from
 or based on this library.  If you modify this library, you may extend
 this exception to your version of the library, but you are not
 obligated to do so.  If you do not wish to do so, delete this
 exception statement from your version. */

//  Overrides global 'new' operator with one that does error checking.

#include <new>

#include <UnitTest++.h>
#include "checked_allocations.h"

// We keep a set of allocations, that, for obvious reasons, does not itself use the 'new' operator.
static AllocationSet* __allocations = NULL;

// Override global definition of new and delete!
void* operator new(size_t size) throw (std::bad_alloc) {
    if (!__allocations) {
        // This uses placement-new, which calls the constructor on a specific memory location
        // This is needed because we cannot call 'new' in this context, nor can we rely on static-initialization
        // for the set to occur before any call to 'new'!
        void* memory = malloc(sizeof(AllocationSet));
        __allocations = new (memory) AllocationSet();
    }

    void* mem = malloc(size);
    if (mem == 0) {
        throw std::bad_alloc(); // ANSI/ISO compliant behavior
    }
    __allocations->insert(mem);
    return mem;
}

void operator delete(void* ptr) throw () {
    if (__allocations->erase(ptr)) {
        free(ptr);
    } else {
        printf(
                "Attempt to free memory with operator 'delete' that was not allocated by 'new'!\n");
        CHECK(false);
    }
}

int cpp_unfreed_allocations() {
    return __allocations->size();
}
