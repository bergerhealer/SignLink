package com.bergerkiller.bukkit.sl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.ToggledState;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

/**
 * Links multiple (Virtual) Signs together to create a single, long, horizontal line of text which can be altered
 */
public class LinkedSign {
	public BlockLocation location;
	public int line;
	private final ToggledState updateSignOrder = new ToggledState();
	public SignDirection direction;
	private String oldtext;
	private final ArrayList<VirtualSign> displaySigns = new ArrayList<VirtualSign>();
	private static HashSet<Block> loopCheck = new HashSet<Block>(); // Used to prevent server freeze when finding signs

	public LinkedSign(BlockLocation location, int lineAt, SignDirection direction) {
		this.location = location;
		this.line = lineAt;
		this.direction = direction;
	}

	public LinkedSign(String worldname, int x, int y, int z, int lineAt, SignDirection direction) {
		this(new BlockLocation(worldname, x, y, z), lineAt, direction);
	}

	public LinkedSign(Block from, int line) {
		this(new BlockLocation(from), line, SignDirection.NONE);

        VirtualSign sign = VirtualSign.getOrCreate(from);
		if (sign != null) {
			String text = sign.getRealLine(line);
			int peri = text.indexOf("%");
			if (peri != -1 && text.lastIndexOf("%") == peri) {
				//get direction from text
				if (peri == 0) {
					this.direction = SignDirection.RIGHT;
				} else if (peri == text.length() - 1) {
					this.direction = SignDirection.LEFT;
				} else if (text.substring(peri).contains(" ")) {
					this.direction = SignDirection.LEFT;
				} else {
					this.direction = SignDirection.RIGHT;
				}
			}
		}
	}

	public void updateText(boolean wrapAround, String... forplayers){
		setText(this.oldtext, wrapAround, forplayers);
	}

	/**
	 * Gets the full line of text this LinkedSign currently displays
	 * 
	 * @return Line of text
	 */
	public String getText() {
		return this.oldtext;
	}

	public void setText(String value, boolean wrapAround, String... forplayers) {
        oldtext = value;
        if (!SignLink.updateSigns) {
            return; 
        }
        final ArrayList<VirtualSign> signs = getSigns();
        if (signs.isEmpty()) {
            return;
        }
        for (VirtualSign sign : signs) {
            if (!sign.isLoaded()) {
                return;
            }
        }

        // Convert text to a list of styled character tokens
        LinkedList<StyledCharacter> characters = StyledCharacter.getChars(value);

        // If all empty, simply set all signs to an empty string
        // Prevents odd infinite loops trying to pad the signs with wrap-around
        if (characters.isEmpty()) {
            for (VirtualSign sign : signs) {
                sign.setLine(this.line, "", forplayers);
            }
            return;
        }

        // Fill the entire width of the signs with characters to make sure padding is correct
        // When wrapping around, it is filled with characters from the beginning, looping
        // When not wrapping around, we add spaces to the left, right, or alternating depending on sign direction
        int remainingSigns = signs.size();
        if (wrapAround) {
            // Add all current characters to the signs
            int width = 0;
            for (StyledCharacter character : characters) {
                width += character.width;
                if (width > VirtualLines.LINE_WIDTH_LIMIT) {
                    width = character.width;
                    remainingSigns--;
                    if (remainingSigns == 0) {
                        width -= character.width;
                        break;
                    }
                }
            }

            // Fill remaining space by wrapping around the characters
            if (remainingSigns > 0) {
                ArrayList<StyledCharacter> wrappedChars = new ArrayList<StyledCharacter>(characters);
                int wrappedCharIdx = 0;
                while (true) {
                    if (wrappedCharIdx == wrappedChars.size()) {
                        wrappedCharIdx = 0;
                    }
                    StyledCharacter sc = wrappedChars.get(wrappedCharIdx++);
                    width += sc.width;
                    if (width > VirtualLines.LINE_WIDTH_LIMIT) {
                        width = sc.width;
                        if (--remainingSigns == 0) {
                            break;
                        }
                    }
                    characters.addLast(sc);
                }
            }

        } else if (signs.size() > 1 || direction != SignDirection.NONE) {
            // Add spaces left and/or right of the characters array until we run out
            StyledCharacter space_first = characters.getFirst().asSpace();
            StyledCharacter space_last = characters.getLast().asSpace();
            SignDirection space_dir = this.direction;
            boolean alternator = false;
            while (true) {
                if (this.direction == SignDirection.NONE) {
                    alternator = !alternator;
                    space_dir = alternator ? SignDirection.LEFT : SignDirection.RIGHT;
                }
                if (space_dir == SignDirection.LEFT) {
                    characters.addFirst(space_first);
                    if (StyledCharacter.getSignCount(characters) > signs.size()) {
                        characters.removeFirst();
                        break;
                    }
                } else {
                    characters.addLast(space_last);
                    if (StyledCharacter.getSignCount(characters) > signs.size()) {
                        characters.removeLast();
                        break;
                    }
                }
            }
        }

        // Next, add characters to the sign lines until we run out of space on the sign, then reset and move on
        Iterator<VirtualSign> signIter = signs.iterator();
        VirtualSign currentSign = signIter.next();
        LinkedList<StyledCharacter> currentSignStyledLine = new LinkedList<StyledCharacter>();
        int currentLineWidth = 0;
        for (StyledCharacter sc : characters) {
            // Try to add the character width to the next sign until it is full
            currentLineWidth += sc.width;
            if (currentLineWidth > VirtualLines.LINE_WIDTH_LIMIT) {
                // Limit exceeded, this sign can not add this new character
                // Reset the width to only include the new character
                currentLineWidth = sc.width;

                // Compile all styled characters found thus far into a single String-formatted line
                currentSign.setLine(this.line, StyledCharacter.getText(currentSignStyledLine), forplayers);
                currentSignStyledLine.clear();
                if (signIter.hasNext()) {
                    currentSign = signIter.next();
                } else {
                    currentSign = null;
                    break; // done! No more sign space.
                }
            }

            // Add the new character
            currentSignStyledLine.addLast(sc);
        }
        if (currentSignStyledLine.size() > 0 && currentSign != null) {
            currentSign.setLine(this.line, StyledCharacter.getText(currentSignStyledLine), forplayers);
        }
    }
	
	// This is the old 'setText' function that assumes a maximum amount of characters of the text
	// It is here for legacy support purposes, but it is no longer used
	/*
	public void setText(String value, boolean wrapAround, String... forplayers) {
		oldtext = value;
		if (!SignLink.updateSigns) {
			return; 
		}
		final ArrayList<VirtualSign> signs = getSigns();
		if (signs.isEmpty()) {
			return;
		}
		for (VirtualSign sign : signs) {
			if (!sign.isLoaded()) {
				return;
			}
		}

		// If specified, wrap around the text so it fills all signs
		if (wrapAround) {
			// Find out the text size, ignoring the text color at the start if it's the same as the end
			double valueLength = value.length();
			if (value.charAt(0) == StringUtil.CHAT_STYLE_CHAR && value.length() > 1) {
				ChatColor firstColor = StringUtil.getColor(value.charAt(1), null);
				if (firstColor != null) {
					ChatColor lastColor = firstColor;
					for (int i = 0; i < value.length() - 1; i++) {
						if (value.charAt(i) == StringUtil.CHAT_STYLE_CHAR) {
							i++;
							lastColor = StringUtil.getColor(value.charAt(i), lastColor);
						}
					}
					if (firstColor == lastColor) {
						valueLength -= 2.0;
					}
				}
			}
			if (valueLength > 0.0) {
				int appendCount = (int) Math.ceil((double) (signs.size() * VirtualLines.MAX_LINE_LENGTH) / valueLength);
				value = StringUtil.getFilledString(value, appendCount);
			}
		}

		// Get the start offset
		String startline = signs.get(0).getRealLine(this.line);
		int startoffset = startline.indexOf("%");
		if (startoffset == -1) {
			startoffset = 0;
		}
		int maxlength = VirtualLines.MAX_LINE_LENGTH - startoffset;

		// Get the color of the text before this variable
		ChatColor color = ChatColor.BLACK;
		for (int i = 0; i < startoffset; i++) {
			if (startline.charAt(i) == StringUtil.CHAT_STYLE_CHAR) {
				i++;
				color = StringUtil.getColor(startline.charAt(i), color);
			}
		}

		ArrayList<String> bits = new ArrayList<String>();
		ChatColor prevcolor = color;
		StringBuilder lastbit = new StringBuilder(VirtualLines.MAX_LINE_LENGTH);

		// Fix up colors in text because of text being cut-off
		// Appends a color in the right positions
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == StringUtil.CHAT_STYLE_CHAR) {
				if (i < value.length() - 1) {
					i++;
					color = StringUtil.getColor(value.charAt(i), color);
				}
			} else {
				// Handle a change of color
				if (prevcolor != color) {
					if (lastbit.length() < maxlength - 2) {
						// Room to append a color?
						lastbit.append(color);
					} else if (lastbit.length() == maxlength - 2) {
						// Lesser, color is allowed, but not an additional character
						bits.add(lastbit.toString() + color);
						// Prepare for the next full text
						maxlength = VirtualLines.MAX_LINE_LENGTH;
						lastbit.setLength(0);
						if (color != ChatColor.BLACK) {
							lastbit.append(color);
						}
					} else {
						//Greater, color is not allowed
						bits.add(lastbit.toString());
						// Prepare for the next full text
						maxlength = VirtualLines.MAX_LINE_LENGTH;
						lastbit.setLength(0);
						if (color != ChatColor.BLACK) {
							lastbit.append(color);
						}
					}
				}
				lastbit.append(c);
				prevcolor = color;
				if (lastbit.length() == maxlength) {
					bits.add(lastbit.toString());
					// Prepare for the next full text
					maxlength = VirtualLines.MAX_LINE_LENGTH;
					lastbit.setLength(0);
					if (color != ChatColor.BLACK) {
						lastbit.append(color);
					}
				}
			}
		}
		// Add a remaining bit
		if (signs.size() > 1) {
			lastbit.append(StringUtil.getFilledString(" ", maxlength - lastbit.length()));
		}
		bits.add(lastbit.toString());

		// Apply all calculated text bits to the signs
		// When applying, take care of %-signs
		int index = 0;
		for (VirtualSign sign : signs) {
			if (index == bits.size()) {
				//clear the sign
				sign.setLine(this.line, "", forplayers);
			} else {
				String line = sign.getRealLine(this.line);
				if (index == 0 && signs.size() == 1) {
					// Set the value in between the two % %
					String start = line.substring(0, startoffset);
					int endindex = line.lastIndexOf("%");
					if (endindex != -1 && endindex != startoffset) {
						String end = line.substring(endindex + 1);
						line = start + bits.get(0);
						int remainder = VirtualLines.MAX_LINE_LENGTH - line.length() - end.length();
						if (remainder < 0) {
							line = line.substring(0, line.length() + remainder);
						}
						line += end;
					} else {
						line = start + bits.get(0);
					}
				} else if (index == 0) {
					//first, take % in account
					String bit = bits.get(0);
					line = line.substring(0, startoffset) + bit;
				} else if (index == signs.size() - 1) {
					//last, take % in account
					String bit = bits.get(index);
					int endindex = Math.min(line.lastIndexOf("%") + 1, line.length() - 1);
					String end = "";
					if (endindex < line.length() - 1) {
						end = line.substring(endindex);
					}
					endindex = VirtualLines.MAX_LINE_LENGTH - end.length();
					if (endindex > bit.length() - 1) {
						endindex = bit.length() - 1;
					}
					line = bit.substring(0, endindex) + end;
				} else {
					//A sign in the middle, simply set it
					line = bits.get(index);
				}
				sign.setLine(this.line, line, forplayers);
				index++;
			}
		}
	}

	public void update() {
		ArrayList<VirtualSign> signs = getSigns();
		if (!signs.isEmpty()) {
			for (VirtualSign sign : signs) {
				sign.update();
			}
		}
	}
	*/

	/**
	 * Gets the starting Block, the first sign block this Linked Sign shows text on
	 * 
	 * @return Linked sign starting block
	 */
	public Block getStartBlock() {
		return this.location.isLoaded() ? this.location.getBlock() : null;
	}

	/**
	 * Tells this Linked Sign to update the order of the signs, to update how text is divided
	 */
	public void updateSignOrder() {
		this.updateSignOrder.set();
	}

	/**
	 * Gets the signs which this Linked Sign displays text on, validates the signs
	 * 
	 * @return The virtual signs on which this Linked Sign shows text
	 */
	public ArrayList<VirtualSign> getSigns() {
		return this.getSigns(true);
	}

	private boolean validateSigns() {
		if (!this.displaySigns.isEmpty()) {
			for (VirtualSign sign : this.displaySigns) {
				if (!sign.validate()) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private Block nextSign(Block from) {
	    // Check if there is a sign to the left/right of the current one (direction)
	    BlockFace from_facing = BlockUtil.getFacing(from);
        BlockFace direction = FaceUtil.rotate(from_facing, 2);
        if (this.direction == SignDirection.RIGHT) {
            direction = direction.getOppositeFace();
        }
        Block next = from.getRelative(direction);
        if (MaterialUtil.ISSIGN.get(next) && BlockUtil.getFacing(next) == from_facing &&  loopCheck.add(next)) {
            return next;
        }

        // Check if there is another wall sign attached to the same block as the current one
        if (from.getType() == Material.WALL_SIGN) {
            BlockFace attachedSide = BlockUtil.getAttachedFace(from);
            Block attachedBlock = from.getRelative(attachedSide);
            BlockFace cornerDir;
            if (this.direction == SignDirection.RIGHT) {
                cornerDir = FaceUtil.rotate(attachedSide, 2);
            } else {
                cornerDir = FaceUtil.rotate(attachedSide, -2);
            }
            next = attachedBlock.getRelative(cornerDir);
            if (next.getType() == Material.WALL_SIGN && BlockUtil.getAttachedFace(next) == cornerDir.getOppositeFace() && loopCheck.add(next)) {
                return next;
            }
        }
        return null;
	}

	/**
	 * Gets the signs which this Linked Sign displays text on
	 * 
	 * @param validate the signs, True to validate that all signs exist, False to ignore that check
	 * @return The virtual signs on which this Linked Sign shows text
	 */
	public ArrayList<VirtualSign> getSigns(boolean validate) {
		if (!validate) {
			return this.displaySigns;
		}
		Block start = getStartBlock();
		//Unloaded chunk?
		if (start == null) {
		    for (VirtualSign sign : this.displaySigns) {
		        sign.restoreRealLine(this.line);
		    }
			this.displaySigns.clear();
			return this.displaySigns;
		}

		if (validateSigns() && !updateSignOrder.clear()) {
			return displaySigns;
		}

		//Regenerate old signs and return
		ArrayList<VirtualSign> signsRemoved = new ArrayList<VirtualSign>(this.displaySigns);
		this.displaySigns.clear();
		if (MaterialUtil.ISSIGN.get(start)) {
		    VirtualSign startSign = VirtualSign.getOrCreate(start);
			this.displaySigns.add(startSign);
			signsRemoved.remove(startSign);
			if (this.direction == SignDirection.NONE) {
				return displaySigns;
			}

			// Look (recursively) for new signs
			loopCheck.clear();
			loopCheck.add(start);
			while ((start = nextSign(start)) != null) {
				VirtualSign sign = VirtualSign.getOrCreate(start);
				String realline = sign.getRealLine(this.line);
				int index = realline.indexOf('%');
				if (index != -1) {
					// End delimiter - check whether the sign is 'valid'
					// Only if a single % is on the sign, can it be used as a last sign
					if (index == 0 && index == realline.length() - 1) {
						//the only char on the sign - allowed
					} else if (index == 0) {
						//all left - space to the right?
						if (realline.charAt(index + 1) != ' ') break;
					} else if (index == realline.length() - 1) {
						//all right - space to the left?
						if (realline.charAt(index - 1) != ' ') break;
					} else {
						//centered - surrounded by spaces?
						if (realline.charAt(index - 1) != ' ') break;
						if (realline.charAt(index + 1) != ' ') break;
					}
					this.displaySigns.add(sign);
					signsRemoved.remove(sign);
					break;
				}
				this.displaySigns.add(sign);
				signsRemoved.remove(sign);
			}
			if (this.direction == SignDirection.LEFT) {
				Collections.reverse(this.displaySigns);
			}
		}
		for (VirtualSign sign : signsRemoved) {
		    sign.restoreRealLine(this.line);
		}
		return this.displaySigns;
	}
}
