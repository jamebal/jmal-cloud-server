pub fn identifier_for(size: u64, relative_path: &str) -> String {
    let sanitized: String = relative_path
        .chars()
        .filter(|ch| ch.is_ascii_alphanumeric() || *ch == '_' || *ch == '-')
        .collect();
    format!("{size}-{sanitized}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn strips_non_uploader_identifier_chars() {
        assert_eq!(identifier_for(12, "a b/中文.txt"), "12-abtxt");
        assert_eq!(
            identifier_for(5, "dir/file_name-1.txt"),
            "5-dirfile_name-1txt"
        );
    }
}
