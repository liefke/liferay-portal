
package com.liferay.petra.mail;

import com.liferay.petra.string.CharPool;
import java.io.IOException;
import java.io.StringReader;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML.Attribute;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/**
 * Converts an HTML segment to plain text. Used by the {@link MailEngine} to
 * automatically convert HTML mails if necessary.
 *
 * @author Tobias Liefke
 */
public class HtmlToPlainTextConverter {

	private static final class HtmlCallback
		extends HTMLEditorKit.ParserCallback {

		private static final int MAX_LINE_LENGTH = 72;

		private final StringBuilder plainText = new StringBuilder();

		private boolean preformatted;

		private String url;

		private void appendBlockBreak() {

			trimTrailingSpaces();

			// Append a new line, if necessary
			if (plainText.length() > 0) {
				final char c =
					plainText.charAt(plainText.length() - 1);
				if (c == CharPool.NEW_LINE || c == CharPool.RETURN) {
					if (plainText.length() > 1 && plainText.charAt(
						plainText.length() - 2) != CharPool.NEW_LINE) {
						plainText.append(CharPool.NEW_LINE);
					}
				}
				else {
					plainText.append("\n\n");
				}
			}
		}

		public String getPlainText() {

			return plainText.toString();
		}

		@Override
		public void handleComment(final char[] data, final int pos) {

			// Ignore comments
		}

		@Override
		public void handleEndTag(final Tag tag, final int pos) {

			if (tag.isPreformatted()) {
				preformatted = false;
			}
			else if (tag.isBlock()) {
				appendBlockBreak();
			}
			else if (tag == Tag.A && url != null) {
				// Add URL, if it is not already part of the preceding plain
				// text
				if (plainText.indexOf(
					url,
					Math.max(
						0, plainText.length() - url.length() -
							url.length() / 2)) < 0) {
					trimTrailingSpaces();
					plainText.append(":\n").append(url).append("\n");
				}
				url = null;
			}
		}

		@Override
		public void handleError(final String errorMsg, final int pos) {

			// Ignore errors
		}

		@Override
		public void handleSimpleTag(
			final Tag tag, final MutableAttributeSet attributes,
			final int pos) {

			handleStartTag(tag, attributes, pos);
		}

		@Override
		public void handleStartTag(
			final Tag tag, final MutableAttributeSet attributes,
			final int pos) {

			if (tag.isBlock()) {
				// Append two newlines
				appendBlockBreak();
			}
			else if (tag == Tag.A) {
				// Remember the URL for addition at the end of the link
				url = (String)attributes.getAttribute(Attribute.HREF);
			}
			else if (tag == Tag.HR) {

				appendBlockBreak();

				for (int i = 0; i < MAX_LINE_LENGTH; i++) {
					plainText.append(CharPool.DASH);
				}

				appendBlockBreak();
			}
			else if (tag.breaksFlow()) {

				trimTrailingSpaces();

				if (tag == Tag.BR ||
					plainText.length() > 0 && plainText.charAt(
						plainText.length() - 1) != CharPool.NEW_LINE) {

					plainText.append(CharPool.NEW_LINE);
				}
			}
		}

		@Override
		public void handleText(final char[] data, final int pos) {

			if (preformatted) {
				// Use the preformatted text
				plainText.append(data);
			}
			else if (data.length > 0) {
				// Normalize the text
				final String text = new String(data).replaceAll("\\s+", " ");

				// Wrap long text
				final boolean precedingWhitespace =
					plainText.length() == 0 || Character.isWhitespace(
						plainText.charAt(plainText.length() - 1));

				int nextSpace = text.indexOf(CharPool.SPACE);
				int lineStart = plainText.lastIndexOf("\n") + 1;

				int start;
				if (!precedingWhitespace) {
					if (nextSpace < 0) {
						// Nothing to wrap
						plainText.append(text);
						return;
					}

					// Add everything including the next whitespace
					plainText.append(text, 0, nextSpace + 1);
					start = nextSpace + 1;
					nextSpace = text.indexOf(CharPool.SPACE, start);
				}
				else if (nextSpace == 0) {
					// Skip the first space, as there is already one at the end
					start = 1;
					nextSpace = text.indexOf(CharPool.SPACE, start);
				}
				else {
					// We are pointing at the word start
					start = 0;
				}

				// Add every word and wrap if necessary
				while (nextSpace > 0) {

					lineStart = wrapIfNecessary(lineStart, start, nextSpace);
					plainText.append(text, start, nextSpace + 1);

					start = nextSpace + 1;
					nextSpace = text.indexOf(CharPool.SPACE, start);
				}

				// Wrap the last word, if necessary
				wrapIfNecessary(lineStart, start, text.length());

				plainText.append(text, start, text.length());
			}
		}

		private void trimTrailingSpaces() {

			// Trim spaces and tabs at the end of the string
			for (int length =
				plainText.length(); length > 0; plainText.setLength(
					--length)) {

				final char c =
					plainText.charAt(plainText.length() - 1);

				if (c != CharPool.SPACE && c != CharPool.TAB) {
					return;
				}
			}
		}

		private int wrapIfNecessary(
			final int lineStart, final int wordStart, final int wordEnd) {

			if (plainText.length() - lineStart + wordEnd -
				wordStart <= MAX_LINE_LENGTH) {

				return lineStart;
			}

			trimTrailingSpaces();

			plainText.append(CharPool.NEW_LINE);

			return plainText.length();
		}

	}

	/**
	 * Converts the given HTML segment to plain text.
	 *
	 * @param html
	 *            the HTML segment
	 * @return plain text for a mail
	 */
	public String convert(final String html) {
		try {

			final HtmlCallback callback = new HtmlCallback();

			new ParserDelegator().parse(new StringReader(html), callback, true);

			return callback.getPlainText();
		}
		catch (final IOException e) {
			// Can't happen when reading from a string reader
			throw new IllegalArgumentException(e);
		}
	}

}