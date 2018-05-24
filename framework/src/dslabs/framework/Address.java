/*
 * Copyright (c) 2018 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework;

import java.io.Serializable;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.builder.CompareToBuilder;

/**
 * Addresses are opaque objects that uniquely identify {@link Node}s. While the
 * provided addresses might provide meaningful information via {@link
 * Object#toString()}, you should not use those {@link String}s (except when
 * printing addresses). Instead, use {@link Object#equals(Object)} and {@link
 * #compareTo(Object)} to compare addresses.
 */
public interface Address extends Serializable, Comparable<Address> {

    // TODO: call root address everywhere in test framework

    /**
     * Returns the root address, representing the root {@link Node} of some
     * hierarchy.
     *
     * @return the root address
     */
    default Address rootAddress() {
        return this;
    }

    /**
     * Returns a sub-address for the given address. Used to initialize a
     * sub-node.
     *
     * @param address
     *         the address of the parent node
     * @param id
     *         the sub-node's identifier
     * @return the sub-address.
     */
    static Address subAddress(Address address, String id) {
        return new SubAddress(address, id);
    }
}

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
class SubAddress implements Address {
    @Getter(AccessLevel.PACKAGE) @NonNull private final Address parentAddress;
    @Getter(AccessLevel.PACKAGE) @NonNull private final String id;

    @Override
    public int compareTo(@Nonnull Address o) {
        // TODO: check this method

        if (!(o instanceof SubAddress)) {
            if (Objects.equals(parentAddress, o)) {
                return -1;
            }
            return parentAddress.compareTo(o);
        }

        SubAddress sa = (SubAddress) o;
        return new CompareToBuilder().append(parentAddress, sa.parentAddress)
                                     .append(id, sa.id).toComparison();
    }

    @Override
    public String toString() {
        return String.format("%s/%s", parentAddress, id);
    }

    @Override
    public Address rootAddress() {
        return parentAddress.rootAddress();
    }
}
