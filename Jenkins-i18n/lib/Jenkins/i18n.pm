package Jenkins::i18n;

use 5.014004;
use strict;
use warnings;
use Config::Properties 1.80;

=pod

=head1 NAME

Jenkins::i18n - functions for translation-tool

=head1 SYNOPSIS

  use Jenkins::i18n qw(remove_unused);

=head1 DESCRIPTION

C<translation-tool.pl> is a CLI program used to help translating the Jenkins
properties file.

This module implements the functions used by the CLI.

=cut

require Exporter;
our @ISA = qw(Exporter);
our @EXPORT = qw(
    remove_unused
);

our $VERSION = '0.01';

=head2 EXPORT

None by default.

=head2 FUNCTIONS

=head3 remove_unused

Remove unused keys from a properties file.

Each translation in every language depends on the original properties files
that are written in English.

This function gets a set of keys and compare with those that are stored in the
translation file: anything that exists outside the original set in English is
considered deprecated and so removed.

Expects as positional parameters:

=over

=item 1

file: the complete path to the translation file to be checked.

=item 2

keys: a L<Set::Tiny> instance of the keys from the original English properties file.

=item 3

license: a scalar reference with a license to include the header of the translated properties file. Optional.

=item 4

backup: a boolean (0 or 1) if a backup file should be created in the same path of the file parameter. Optional.

=back

Returns the number of keys removed (as an integer).

=cut

sub remove_unused {
    my $file = shift;
    die "file is a required parameter\n" unless ( defined($file) );
    my $keys = shift;
    die "keys is a required parameter\n" unless ( defined($keys) );
    die "keys must be a Set::Tiny instance\n"
        unless ( ref($keys) eq 'Set::Tiny' );
    my $license_ref = shift;
    die "license must be an scalar reference"
        if ( defined($license_ref) and ( ref($license_ref) ne 'SCALAR' ) );
    my $use_backup = shift;
    $use_backup = 0 unless ( defined($use_backup) );

    my $props_handler;

    if ($use_backup) {
        my $backup = "$file.bak";
        rename( $file, $backup )
            or die "Cannot rename $file to $backup: $!\n";
        $props_handler = Config::Properties->new( file => $backup );
    }
    else {
        $props_handler = Config::Properties->new( file => $file );
    }

    my %curr_props = $props_handler->properties;
    my $removed    = 0;

    foreach my $key ( keys(%curr_props) ) {
        $removed++ unless ( $keys->has($key) );
    }

    open( my $out, '>', $file ) or die "Cannot write to $file: $!\n";

    if ($license_ref) {
        $props_handler->save( $out, $$license_ref );
    }
    else {
        $props_handler->save($out);
    }
    close($out) or die "Cannot save $file: $!\n";

    return $removed;
}

1;
__END__


=head1 SEE ALSO

=over

=item *

L<Config::Properties>

=item *

L<Set::Tiny>

=back

=head1 AUTHOR

Alceu Rodrigues de Freitas Junior, E<lt>arfreitas@cpan.orgE<gt>

=head1 COPYRIGHT AND LICENSE

The MIT License

Copyright (C) 2022 by Alceu Freitas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

=cut
