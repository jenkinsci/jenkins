use warnings;
use strict;
use Test::More tests => 15;
use File::Spec;
use File::Copy;
use Set::Tiny 0.04;
use Test::TempDir::Tiny 0.018;
use Test::Exception 0.43;

use Jenkins::i18n qw(remove_unused);

my $removed;
my $expected_removed = 12;
my $required_regex = qr/required\sparameter/;

dies_ok { $removed = remove_unused() } 'dies without file parameter';
like $@, $required_regex, 'get the expected error message';

my $temp_dir  = tempdir();
my $props     = 't/samples/table_pt_BR.properties';
my $tmp_props = File::Spec->catfile( $temp_dir, 'sample.properties' );
copy( $props, $tmp_props ) or die "Copy $!\n";
note("Using $tmp_props for tests");

dies_ok { $removed = remove_unused($tmp_props) }
'dies without keys parameter';
like $@, $required_regex, 'get the expected error message';
dies_ok { $removed = remove_unused( $tmp_props, 'foo' ) }
'dies with invalid keys parameter';
like $@, qr/Set::Tiny/, 'get the expected error message';

my $keys = Set::Tiny->new(qw(Install compatWarning));
note('Without a license');
$removed = remove_unused( $tmp_props, $keys );
is( $removed, $expected_removed, 'got the expected number of keys removed' );
is( read_and_count($tmp_props),
    29, 'resulting properties file has the expected number of lines' );

dies_ok { $removed = remove_unused( $tmp_props, $keys, 'fffff' ) }
'dies with invalid license parameter';
like $@, qr/scalar\sreference/, 'get the expected error message';

my $license = <<BLOCK;
This is
a license
something
BLOCK

note('Restoring file');
copy( $props, $tmp_props ) or die "Copy $!\n";
my $expected_lines = 34;

note('With a license');
$removed = remove_unused( $tmp_props, $keys, \$license );
is( $removed, $expected_removed, 'got the expected number of keys removed' );
is( read_and_count($tmp_props),
    $expected_lines, 'resulting properties file has the expected number of lines' );

note('Restoring file');
copy( $props, $tmp_props ) or die "Copy $!\n";

note('With a backup');
$removed = remove_unused( $tmp_props, $keys, \$license, 1 );
is( $removed, $expected_removed, 'got the expected number of keys removed' );
is( read_and_count($tmp_props),
    $expected_lines, 'resulting properties file has the expected number of lines' );
my $backup = "$tmp_props.bak";
ok(-s $backup, "File has a backup as expected at $backup");

sub read_and_count {
    my $file         = shift;
    my $line_counter = 0;
    open( my $in, '<', $file ) or die "Cannot read $file: $!\n";
    while (<$in>) {
        $line_counter++;
    }
    close($in);
    return $line_counter;
}

# -*- mode: perl -*-
# vi: set ft=perl :
