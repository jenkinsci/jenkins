package Jenkins::i18n;

use 5.014004;
use strict;
use warnings;
use Config::Properties 1.80;

=pod

=head1 NAME

Jenkins::i18n - Perl extension for blah blah blah

=head1 SYNOPSIS

  use Jenkins::i18n;
  blah blah blah

=head1 DESCRIPTION

Stub documentation for Jenkins::i18n, created by h2xs. It looks like the
author of the extension was negligent enough to leave the stub
unedited.

Blah blah blah.


=cut

require Exporter;
our @ISA = qw(Exporter);
our @EXPORT = qw(
    remove_unused
);

our $VERSION = '0.01';

=head2 FUNCTIONS

=head3 remove_unused

Remove unused keys from a file.

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

   #    if (rename($ofile, $back) && open(FI, $back) && open(FO, ">$ofile")) {
   #        my $cont = 0;
   #        while (<FI>) {
   #            if (!$cont) {
   #                if (/^([^#\s].*?[^\\])=(.*)[\s\\]*$/) {
   #                    if (!$keys{$1}) {
   #                        $cont = (/\\\s*$/) ? 1 : 0;
   #                        next;
   #                    }
   #                }
   #                print FO $_;
   #            } elsif ($cont && !/\\\s*$/) {
   #                $cont = 0;
   #            }
   #        }
   #        close(FI);
   #        close(FO);
   #    }

    #    unlink($backup) or die "Cannot remove the backup file $backup: $!\n";
    return $removed;
}

# Preloaded methods go here.

1;
__END__

=head2 EXPORT

None by default.



=head1 SEE ALSO

=over

=item *

L<Config::Properties>

=back

=head1 AUTHOR

Alceu Freitas, E<lt>semantix@(none)E<gt>

=head1 COPYRIGHT AND LICENSE

Copyright (C) 2022 by Alceu Freitas

This library is free software; you can redistribute it and/or modify
it under the same terms as Perl itself, either Perl version 5.34.0 or,
at your option, any later version of Perl 5 you may have available.


=cut
